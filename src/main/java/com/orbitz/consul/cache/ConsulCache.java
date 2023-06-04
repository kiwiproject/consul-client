package com.orbitz.consul.cache;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.orbitz.consul.async.ConsulResponseCallback;
import com.orbitz.consul.config.CacheConfig;
import com.orbitz.consul.model.ConsulResponse;
import com.orbitz.consul.monitoring.ClientEventHandler;
import com.orbitz.consul.option.ImmutableQueryOptions;
import com.orbitz.consul.option.QueryOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * A cache structure that can provide an up-to-date read-only
 * map backed by consul data
 *
 * @param <K> the type of keys this cache contains
 * @param <V> the type of values this cache contains
 */
public class ConsulCache<K, V> implements AutoCloseable {
    enum State {
        LATENT, STARTING, STARTED, STOPPED
    }

    private static final Logger LOG = LoggerFactory.getLogger(ConsulCache.class);

    private final AtomicReference<BigInteger> latestIndex = new AtomicReference<>(null);
    private final AtomicLong lastContact = new AtomicLong();
    private final AtomicBoolean isKnownLeader = new AtomicBoolean();
    private final AtomicReference<ConsulResponse.CacheResponseInfo> lastCacheInfo = new AtomicReference<>(null);
    private final AtomicReference<ImmutableMap<K, V>> lastResponse = new AtomicReference<>(null);
    private final AtomicReference<State> state = new AtomicReference<>(State.LATENT);
    private final CountDownLatch initLatch = new CountDownLatch(1);
    private final Scheduler scheduler;
    private final CopyOnWriteArrayList<Listener<K, V>> listeners = new CopyOnWriteArrayList<>();
    private final ReentrantLock listenersStartingLock = new ReentrantLock();
    private final Stopwatch stopWatch = Stopwatch.createUnstarted();

    private final Function<V, K> keyConversion;
    private final CallbackConsumer<V> callBackConsumer;
    private final ConsulResponseCallback<List<V>> responseCallback;
    private final ClientEventHandler eventHandler;
    private final CacheDescriptor cacheDescriptor;

    protected ConsulCache(
            Function<V, K> keyConversion,
            CallbackConsumer<V> callbackConsumer,
            CacheConfig cacheConfig,
            ClientEventHandler eventHandler,
            CacheDescriptor cacheDescriptor) {

        this(keyConversion, callbackConsumer, cacheConfig, eventHandler, cacheDescriptor, createDefault());
    }

    protected ConsulCache(
            Function<V, K> keyConversion,
            CallbackConsumer<V> callbackConsumer,
            CacheConfig cacheConfig,
            ClientEventHandler eventHandler,
            CacheDescriptor cacheDescriptor,
            ScheduledExecutorService callbackScheduleExecutorService) {

        this(keyConversion, callbackConsumer, cacheConfig, eventHandler, cacheDescriptor, new ExternalScheduler(callbackScheduleExecutorService));
    }

    protected ConsulCache(
            Function<V, K> keyConversion,
            CallbackConsumer<V> callbackConsumer,
            CacheConfig cacheConfig,
            ClientEventHandler eventHandler,
            CacheDescriptor cacheDescriptor,
            Scheduler callbackScheduler) {

        checkArgument(nonNull(keyConversion), "keyConversion must not be null");
        checkArgument(nonNull(callbackConsumer), "callbackConsumer must not be null");
        checkArgument(nonNull(cacheConfig), "cacheConfig must not be null");
        checkArgument(nonNull(eventHandler), "eventHandler must not be null");
        checkArgument(nonNull(cacheDescriptor), "cacheDescriptor must not be null");
        checkArgument(nonNull(callbackScheduler), "callbackScheduler must not be null");

        this.keyConversion = keyConversion;
        this.callBackConsumer = callbackConsumer;
        this.eventHandler = eventHandler;
        this.cacheDescriptor = cacheDescriptor;
        this.scheduler = callbackScheduler;

        this.responseCallback = new DefaultConsulResponseCallback(cacheConfig);
    }

    /**
     * @implNote This was extracted from an anonymous class declaration into a separate class mainly
     * for organization and (somewhat) better readability. Several small methods were also extracted
     * such as notifyListeners, and the updateIndex method was moved into this class since it is
     * only used here. It cannot be static because it uses instance fields from ConsulCache directly.
     * It might be possible to make it static if we pass in the required fields to the constructor, since
     * they are accessed only via their methods and are not reassigned.
     */
    class DefaultConsulResponseCallback implements ConsulResponseCallback<List<V>> {

        private final CacheConfig cacheConfig;

        public DefaultConsulResponseCallback(CacheConfig cacheConfig) {
            this.cacheConfig = requireNonNull(cacheConfig);
        }

        @Override
        public void onComplete(ConsulResponse<List<V>> consulResponse) {
            if (isNotRunning()) {
                return;
            }

            long elapsedTime = stopWatch.elapsed(TimeUnit.MILLISECONDS);
            updateIndex(consulResponse);
            LOG.debug("Consul cache updated for {} (index={}), request duration: {} ms",
                    cacheDescriptor, latestIndex, elapsedTime);

            ImmutableMap<K, V> full = convertToMap(consulResponse);

            boolean changed = !full.equals(lastResponse.get());
            eventHandler.cachePollingSuccess(cacheDescriptor, changed, elapsedTime);

            if (changed) {
                // changes
                lastResponse.set(full);
                // metadata changes
                lastContact.set(consulResponse.getLastContact());
                isKnownLeader.set(consulResponse.isKnownLeader());

                boolean locked = false;
                if (state.get() == State.STARTING) {
                    listenersStartingLock.lock();
                    locked = true;
                }
                try {
                    notifyListeners(full);
                }
                finally {
                    if (locked) {
                        listenersStartingLock.unlock();
                    }
                }
            }

            if (state.compareAndSet(State.STARTING, State.STARTED)) {
                initLatch.countDown();
            }

            Duration timeToWait = cacheConfig.getMinimumDurationBetweenRequests();
            Duration minimumDelayOnEmptyResult = cacheConfig.getMinimumDurationDelayOnEmptyResult();
            if (hasNullOrEmptyResponse(consulResponse) && isLongerThan(minimumDelayOnEmptyResult, timeToWait)) {
                timeToWait = minimumDelayOnEmptyResult;
            }
            timeToWait = timeToWait.minusMillis(elapsedTime);

            scheduler.schedule(ConsulCache.this::runCallback, timeToWait.toMillis(), TimeUnit.MILLISECONDS);
        }

        private void updateIndex(ConsulResponse<List<V>> consulResponse) {
            if (nonNull(consulResponse) && nonNull(consulResponse.getIndex())) {
                latestIndex.set(consulResponse.getIndex());
            }
        }

        private void notifyListeners(ImmutableMap<K, V> newValues) {
            for (Listener<K, V> l : listeners) {
                try {
                    l.notify(newValues);
                } catch (RuntimeException e) {
                    LOG.warn("ConsulCache Listener's notify method threw an exception.", e);
                }
            }
        }

        private boolean hasNullOrEmptyResponse(ConsulResponse<List<V>> consulResponse) {
            return isNull(consulResponse.getResponse()) || consulResponse.getResponse().isEmpty();
        }

        private boolean isLongerThan(Duration duration1, Duration duration2) {
            return duration1.compareTo(duration2) > 0;
        }

        @Override
        public void onFailure(Throwable throwable) {
            if (isNotRunning()) {
                return;
            }

            eventHandler.cachePollingError(cacheDescriptor, throwable);
            long delayMs = computeBackOffDelayMs(cacheConfig);
            String message = String.format("Error getting response from consul for %s, will retry in %d %s",
                    cacheDescriptor, delayMs, TimeUnit.MILLISECONDS);

            cacheConfig.getRefreshErrorLoggingConsumer().accept(LOG, message, throwable);

            scheduler.schedule(ConsulCache.this::runCallback, delayMs, TimeUnit.MILLISECONDS);
        }

        private boolean isNotRunning() {
            return !isRunning();
        }
    }

    static long computeBackOffDelayMs(CacheConfig cacheConfig) {
        return cacheConfig.getMinimumBackOffDelay().toMillis() +
                Math.round(Math.random() * (cacheConfig.getMaximumBackOffDelay().minus(cacheConfig.getMinimumBackOffDelay()).toMillis()));
    }

    public void start() {
        checkState(state.compareAndSet(State.LATENT, State.STARTING),"Cannot transition from state %s to %s", state.get(), State.STARTING);
        eventHandler.cacheStart(cacheDescriptor);
        runCallback();
    }

    public void stop() {
        try {
            eventHandler.cacheStop(cacheDescriptor);
        } catch (RejectedExecutionException ree) {
            LOG.error("Unable to propagate cache stop event. ", ree);
        }

        State previous = state.getAndSet(State.STOPPED);
        if (stopWatch.isRunning()) {
            stopWatch.stop();
        }
        if (previous != State.STOPPED) {
            scheduler.shutdownNow();
        }
    }

    @Override
    public void close() {
        stop();
    }

    private void runCallback() {
        if (isRunning()) {
            stopWatch.reset().start();
            callBackConsumer.consume(latestIndex.get(), responseCallback);
        }
    }

    private boolean isRunning() {
        return state.get() == State.STARTED || state.get() == State.STARTING;
    }

    public boolean awaitInitialized(long timeout, TimeUnit unit) throws InterruptedException {
        return initLatch.await(timeout, unit);
    }

    public ImmutableMap<K, V> getMap() {
        return lastResponse.get();
    }

    public ConsulResponse<ImmutableMap<K,V>> getMapWithMetadata() {
        return new ConsulResponse<>(lastResponse.get(), lastContact.get(), isKnownLeader.get(), latestIndex.get(), Optional.ofNullable(lastCacheInfo.get()));
    }

    @VisibleForTesting
    ImmutableMap<K, V> convertToMap(final ConsulResponse<List<V>> response) {
        if (isNull(response) || isNull(response.getResponse()) || response.getResponse().isEmpty()) {
            return ImmutableMap.of();
        }

        ImmutableMap.Builder<K, V> builder = ImmutableMap.builder();
        Set<K> keySet = new HashSet<>();
        for (V v : response.getResponse()) {
            K key = keyConversion.apply(v);
            if (nonNull(key)) {
                if (keySet.contains(key)) {
                    LOG.warn("Duplicate service encountered. May differ by tags. Try using more specific tags? {}", key);
                } else {
                    builder.put(key, v);
                    keySet.add(key);
                }
            }
        }

        return builder.build();
    }

    protected static QueryOptions watchParams(BigInteger index, int blockSeconds, QueryOptions queryOptions) {
        checkArgument(queryOptions.getIndex().isEmpty() && queryOptions.getWait().isEmpty(),
                "Index and wait cannot be overridden");

        ImmutableQueryOptions.Builder builder =  ImmutableQueryOptions.builder()
                .from(watchDefaultParams(index, blockSeconds))
                .token(queryOptions.getToken())
                .consistencyMode(queryOptions.getConsistencyMode())
                .near(queryOptions.getNear())
                .datacenter(queryOptions.getDatacenter());
        for (String tag : queryOptions.getTag()) {
            builder.addTag(tag);
        }
        return builder.build();
    }

    private static QueryOptions watchDefaultParams(final BigInteger index, final int blockSeconds) {
        if (isNull(index)) {
            return QueryOptions.BLANK;
        } else {
            return QueryOptions.blockSeconds(blockSeconds, index).build();
        }
    }

    protected static Scheduler createDefault() {
        return new DefaultScheduler();
    }

    protected static Scheduler createExternal(ScheduledExecutorService executor) {
        return new ExternalScheduler(executor);
    }

    /**
     * passed in by creators to vary the content of the cached values
     *
     * @param <V> the type of values to be consumed
     */
    protected interface CallbackConsumer<V> {
        void consume(BigInteger index, ConsulResponseCallback<List<V>> callback);
    }

    /**
     * Implementers can register a listener to receive
     * a new map when it changes
     *
     * @param <K> the type of keys
     * @param <V> the type of values
     */
    public interface Listener<K, V> {
        void notify(Map<K, V> newValues);
    }

    /**
     * Add a new listener.
     *
     * @param listener the listener to add
     * @return true to indicate the listener was added
     * @implNote This always returns true because {@link CopyOnWriteArrayList} is used to store the listeners, and
     * its {@link CopyOnWriteArrayList#add(Object)} method always returns true
     */
    public boolean addListener(Listener<K, V> listener) {
        boolean locked = false;
        if (state.get() == State.STARTING) {
            listenersStartingLock.lock();
            locked = true;
        }
        try {
            listeners.add(listener);
            if (state.get() == State.STARTED) {
                try {
                    listener.notify(lastResponse.get());
                } catch (RuntimeException e) {
                    LOG.warn("ConsulCache Listener's notify method threw an exception.", e);
                }
            }
        } finally {
            if (locked) {
                listenersStartingLock.unlock();
            }
        }
        return true;
    }

    public List<Listener<K, V>> getListeners() {
        return List.copyOf(listeners);
    }

    public boolean removeListener(Listener<K, V> listener) {
        return listeners.remove(listener);
    }

    @VisibleForTesting
    protected State getState() {
        return state.get();
    }

    protected static class Scheduler {
        public Scheduler(ScheduledExecutorService executor) {
            this.executor = executor;
        }

        void schedule(Runnable r, long delay, TimeUnit unit) {
            executor.schedule(r, delay, unit);
        }

        void shutdownNow() {
            executor.shutdownNow();
        }

        private final ScheduledExecutorService executor;
    }

    private static class DefaultScheduler extends Scheduler {
        public DefaultScheduler() {
            super(Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder()
                    .setNameFormat("consulCacheScheduledCallback-%d")
                    .setDaemon(true)
                    .build()));
        }
    }

    private static class ExternalScheduler extends Scheduler {

        public ExternalScheduler(ScheduledExecutorService executor) {
            super(executor);
        }

        @Override
        public void shutdownNow() {
            // do nothing, since executor was externally created
        }
    }

    protected static void checkWatch(int networkReadMillis, int cacheWatchSeconds) {
        if (networkReadMillis <= TimeUnit.SECONDS.toMillis(cacheWatchSeconds)) {
            throw new IllegalArgumentException("Cache watchInterval="+ cacheWatchSeconds + "sec >= networkClientReadTimeout="
                + networkReadMillis + "ms. It can cause issues");
        }
    }
}
