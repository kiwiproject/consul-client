package org.kiwiproject.consul.cache;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.kiwiproject.consul.async.ConsulResponseCallback;
import org.kiwiproject.consul.config.CacheConfig;
import org.kiwiproject.consul.model.ConsulResponse;
import org.kiwiproject.consul.monitoring.ClientEventHandler;
import org.kiwiproject.consul.option.ImmutableQueryOptions;
import org.kiwiproject.consul.option.Options;
import org.kiwiproject.consul.option.QueryOptions;
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
import java.util.function.Supplier;

/**
 * Asynchronous, read-only snapshot cache backed by Consul.
 * <p>
 * {@code ConsulCache} repeatedly queries Consul (optionally using Consul’s
 * <em>blocking query</em> mechanism for long polling) via a supplied
 * {@link CallbackConsumer}. It maintains an immutable snapshot map of the
 * latest data, keyed by a caller-provided {@code keyConversion} function.
 * <p>
 * Callers can:
 * <ul>
 *   <li>Start/stop the cache via {@link #start()} / {@link #stop()} (also {@link #close()}).</li>
 *   <li>Read the current snapshot with {@link #getMap()} or {@link #getMapWithMetadata()}.</li>
 *   <li>Wait for the first successful fetch with {@link #awaitInitialized(long, java.util.concurrent.TimeUnit)}.</li>
 *   <li>Register {@link Listener}s to be notified when the snapshot changes.</li>
 * </ul>
 *
 * <h3>Threading & notifications</h3>
 * All public methods are safe for concurrent use. Listener callbacks are invoked on the cache’s internal
 * scheduler thread; implementations should return quickly and offload expensive work.
 *
 * <h3>Subclassing</h3>
 * This class is intended to be subclassed: constructors are {@code protected}. Typical subclasses bind a
 * specific Consul endpoint by supplying:
 * <ul>
 *   <li>a {@code CallbackConsumer<V>} that enqueues the Retrofit call (usually a blocking query), and</li>
 *   <li>a {@code Function<V,K>} that derives keys for the snapshot map.</li>
 * </ul>
 * Subclasses usually expose public factory methods that call one of the protected constructors; overriding
 * lifecycle methods is not required.
 *
 * @param <K> the type of keys this cache contains
 * @param <V> the type of values this cache contains
 */
public class ConsulCache<K, V> implements AutoCloseable {

    /**
     * Represents the possible states of a ConsulCache.
     */
    public enum State {
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
    private final Stopwatch stopwatch = Stopwatch.createUnstarted();
    private final ReentrantLock stopwatchLock = new ReentrantLock();

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

            var elapsedTimeMillis = withStopwatchLock(() -> stopwatch.elapsed(TimeUnit.MILLISECONDS));
            updateIndex(consulResponse);
            LOG.debug("Consul cache updated for {} (index={}), request duration: {} ms",
                    cacheDescriptor, latestIndex, elapsedTimeMillis);

            ImmutableMap<K, V> full = convertToMap(consulResponse);

            boolean changed = !full.equals(lastResponse.get());
            eventHandler.cachePollingSuccess(cacheDescriptor, changed, elapsedTimeMillis);

            // metadata changes; always set
            lastContact.set(consulResponse.getLastContact());
            isKnownLeader.set(consulResponse.isKnownLeader());
            lastCacheInfo.set(consulResponse.getCacheResponseInfo().orElse(null));

            if (changed) {
                // changes
                lastResponse.set(full);

                performListenerActionOptionallyLocking(() -> notifyListeners(full));
            }

            if (state.compareAndSet(State.STARTING, State.STARTED)) {
                initLatch.countDown();
            }

            Duration timeToWait = cacheConfig.getMinimumDurationBetweenRequests();
            Duration minimumDelayOnEmptyResult = cacheConfig.getMinimumDurationDelayOnEmptyResult();
            if (hasNullOrEmptyResponse(consulResponse) && isLongerThan(minimumDelayOnEmptyResult, timeToWait)) {
                timeToWait = minimumDelayOnEmptyResult;
            }
            timeToWait = timeToWait.minusMillis(elapsedTimeMillis);
            if (timeToWait.isNegative()) {
                // ensure a minimum non-negative wait time
                timeToWait = Duration.ofMillis(1);
            }

            scheduleRunCallbackSafely(timeToWait.toMillis());
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

            scheduleRunCallbackSafely(delayMs);
        }

        private void scheduleRunCallbackSafely(long delayMillis) {
            scheduleRunCallbackSafely(isRunning(), cacheDescriptor, scheduler, delayMillis, ConsulCache.this::runCallback);
        }

        @VisibleForTesting
        static boolean scheduleRunCallbackSafely(boolean isRunning,
                                                 CacheDescriptor descriptor,
                                                 Scheduler scheduler,
                                                 long delayMillis,
                                                 Runnable callback) {

            if (!isRunning) {
                LOG.trace("Ignoring request to schedule next callback for [{}]; the cache is not running", descriptor);
                return false;
            }

            LOG.trace("Scheduling next callback for [{}] with {} ms delay", descriptor, delayMillis);

            try {
                scheduler.schedule(callback, delayMillis, TimeUnit.MILLISECONDS);
                return true;
            } catch (RejectedExecutionException ignored) {
                LOG.debug("Ignoring RejectedExecutionException for {}; scheduler was probably shut down during stop()",
                        descriptor);
                return false;
            }
        }

        private boolean isNotRunning() {
            return !isRunning();
        }
    }

    static long computeBackOffDelayMs(CacheConfig cacheConfig) {
        return cacheConfig.getMinimumBackOffDelay().toMillis() +
                Math.round(Math.random() * (cacheConfig.getMaximumBackOffDelay().minus(cacheConfig.getMinimumBackOffDelay()).toMillis()));
    }

    /**
     * Starts the cache and begins asynchronous polling of Consul data.
     * <p>
     * Transitions the cache from the {@link State#LATENT} state to {@link State#STARTING} and triggers
     * the first Consul request. Once the initial response is received, the cache transitions to
     * {@link State#STARTED}.
     *
     * @throws IllegalStateException if the cache is not in the {@link State#LATENT} state
     */
    public void start() {
        checkState(state.compareAndSet(State.LATENT, State.STARTING),
                "Cannot transition from state %s to %s", state.get(), State.STARTING);
        eventHandler.cacheStart(cacheDescriptor);
        runCallback();
    }

    /**
     * Stops the cache and terminates any scheduled polling of Consul.
     * <p>
     * Transitions the cache to the {@link State#STOPPED} state, shuts down its internal scheduler,
     * and notifies the event handler that the cache has stopped.
     */
    public void stop() {
        try {
            eventHandler.cacheStop(cacheDescriptor);
        } catch (RejectedExecutionException ree) {
            LOG.error("Unable to propagate cache stop event. ", ree);
        }

        State previous = state.getAndSet(State.STOPPED);

        withStopwatchLock(() -> stopIfRunningQuietly(stopwatch));

        if (previous != State.STOPPED) {
            scheduler.shutdownNow();
        }
    }

    /**
     * Closes this cache by delegating to {@link #stop()}.
     * <p>
     * Provided for use with try-with-resources and other {@link AutoCloseable} patterns.
     */
    @Override
    public void close() {
        stop();
    }

    private void runCallback() {
        if (isRunning()) {
            withStopwatchLock(() -> stopwatch.reset().start());
            callBackConsumer.consume(latestIndex.get(), responseCallback);
        }
    }

    private boolean isRunning() {
        return state.get() == State.STARTED || state.get() == State.STARTING;
    }

    /**
     * Waits for the cache to complete its initial population.
     * <p>
     * When {@link #start()} is called, the cache begins asynchronously fetching
     * data from Consul. This method allows callers to block until that initial
     * fetch completes (that is, until the cache reaches the {@link State#STARTED}
     * state) or until the specified timeout elapses.
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit of the {@code timeout} argument
     * @return {@code true} if the cache initialized before the timeout expired;
     * {@code false} if the wait timed out
     * @throws InterruptedException if the current thread is interrupted while waiting
     * @implNote This method delegates to an internal {@link CountDownLatch} that is
     * decremented once the first successful Consul response is received.
     */
    public boolean awaitInitialized(long timeout, TimeUnit unit) throws InterruptedException {
        return initLatch.await(timeout, unit);
    }

    /**
     * Returns the most recent snapshot of key–value data held by this cache.
     * <p>
     * The returned map is an immutable view of the last successfully fetched
     * Consul data. If no data has yet been fetched, an empty map is returned.
     *
     * @return an immutable map of the most recently cached Consul data, or an
     * empty map if the cache has not yet been initialized
     * @see #getMapWithMetadata()
     */
    public ImmutableMap<K, V> getMap() {
        return Optional.ofNullable(lastResponse.get()).orElseGet(ImmutableMap::of);
    }

    /**
     * Returns the most recent cached data along with Consul response metadata.
     * <p>
     * This includes additional information from the last Consul response such as
     * the {@code X-Consul-Index}, last contact time, and leader status.
     * <p>
     * The response’s {@link ConsulResponse#getResponse()} contains the same map
     * as {@link #getMap()} and is never {@code null}; until the cache is initialized,
     * it will be an empty map.
     * <p>
     * <strong>Metadata before initialization:</strong> until the first successful fetch,
     * metadata fields may be unset or defaulted; specifically, {@code getIndex()} may be
     * {@code null}, {@code getCacheResponseInfo()} may be empty, {@code getLastContact()}
     * will be {@code 0}, and {@code isKnownLeader()} will be {@code false}.
     *
     * @return a {@link ConsulResponse} containing the cached data (never {@code null},
     * but possibly empty) and associated metadata; prior to initialization some
     * metadata fields may be absent or hold default values as described above
     * @see #getMap()
     */
    public ConsulResponse<ImmutableMap<K,V>> getMapWithMetadata() {
        return new ConsulResponse<>(
                Optional.ofNullable(lastResponse.get()).orElseGet(ImmutableMap::of),
                lastContact.get(),
                isKnownLeader.get(),
                latestIndex.get(),
                lastCacheInfo.get()
        );
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
            return Options.BLANK_QUERY_OPTIONS;
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
     * Strategy interface that issues a single Consul request (e.g., enqueues a Retrofit call) and
     * delegates result handling to the provided callback.
     * <p>
     * Implementations typically pass the {@code callback} through to the HTTP layer (e.g., via
     * {@code Http#extractConsulResponse(...)}), which wires a Retrofit callback that will invoke
     * {@link ConsulResponseCallback#onComplete(ConsulResponse)} or
     * {@link ConsulResponseCallback#onFailure(Throwable)} when the network response arrives.
     * Implementations themselves should not invoke {@code onComplete} or {@code onFailure}
     * synchronously; they are responsible for initiating the request and propagating the callback.
     * <p>
     * <strong>Contract:</strong>
     * <ul>
     *   <li>May be invoked repeatedly; implementations should be idempotent and thread-safe.</li>
     *   <li>{@code index} may be {@code null} on the first call and must be handled.</li>
     *   <li>Must not throw; report failures by delegating to the HTTP layer so the callback is invoked.</li>
     *   <li>Should honor any timeouts provided by {@link CacheConfig}.</li>
     * </ul>
     *
     * @param <V> the element type contained in the Consul response payload
     */
    protected interface CallbackConsumer<V> {
        void consume(BigInteger index, ConsulResponseCallback<List<V>> callback);
    }

    /**
     * Callback interface notified when the cached data changes.
     * <p>
     * A listener is invoked after the cache processes a successful Consul response and
     * detects that the computed map has changed since the previous update. If a listener
     * is added while the cache is already {@link State#STARTED}, it will immediately
     * receive a snapshot of the current values.
     * <p>
     * <strong>Threading & performance:</strong> notifications are dispatched on the cache's
     * internal scheduler thread. Implementations should return quickly and offload any
     * expensive work to another thread to avoid delaying later polling cycles.
     * <p>
     * The {@code newValues} map provided to {@link #notify(Map)} is an immutable snapshot;
     * callers must not attempt to modify it.
     *
     * @param <K> the type of keys in the cached map
     * @param <V> the type of values in the cached map
     */
    public interface Listener<K, V> {

        /**
         * Called when the cache publishes a new immutable snapshot of its contents.
         *
         * @param newValues the latest immutable snapshot of the cached key–value map; never {@code null}
         */
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
        performListenerActionOptionallyLocking(() -> {
            listeners.add(listener);
            if (state.get() == State.STARTED) {
                try {
                    var snapshot = Optional.ofNullable(lastResponse.get()).orElseGet(ImmutableMap::of);
                    listener.notify(snapshot);
                } catch (RuntimeException e) {
                    LOG.warn("ConsulCache Listener's notify method threw an exception.", e);
                }
            }
        });

        return true;
    }

    private void performListenerActionOptionallyLocking(Runnable action) {
        var locked = false;
        if (state.get() == State.STARTING) {
            listenersStartingLock.lock();
            locked = true;
        }
        try {
            action.run();
        } finally {
            if (locked) {
                listenersStartingLock.unlock();
            }
        }
    }

    /**
     * Returns an immutable snapshot of the currently registered listeners.
     * <p>
     * The returned list is a copy and will not reflect later additions or removals.
     *
     * @return an unmodifiable list of registered listeners in registration order
     */
    public List<Listener<K, V>> getListeners() {
        return List.copyOf(listeners);
    }

    /**
     * Unregisters a previously added listener.
     *
     * @param listener the listener to remove
     * @return {@code true} if the listener was present and removed; {@code false} otherwise
     */
    public boolean removeListener(Listener<K, V> listener) {
        return listeners.remove(listener);
    }

    /**
     * Returns the current lifecycle {@link State} of this cache.
     *
     * @return the current state; one of {@link State#LATENT}, {@link State#STARTING},
     * {@link State#STARTED}, or {@link State#STOPPED}
     */
    public State getState() {
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
            // do nothing, since the executor was externally created
        }
    }

    protected static void checkWatch(int networkReadMillis, int cacheWatchSeconds) {
        if (networkReadMillis <= TimeUnit.SECONDS.toMillis(cacheWatchSeconds)) {
            throw new IllegalArgumentException("Cache watchInterval = "+ cacheWatchSeconds + " sec >= networkClientReadTimeout = "
                + networkReadMillis + " ms. It can cause issues");
        }
    }

    @CanIgnoreReturnValue
    private <T> T withStopwatchLock(Supplier<T> action) {
        stopwatchLock.lock();
        try {
            return action.get();
        } finally {
            stopwatchLock.unlock();
        }
    }

    @VisibleForTesting
    static boolean stopIfRunningQuietly(Stopwatch stopwatch) {
        try {
            if (stopwatch.isRunning()) {
                stopwatch.stop();
                return true;
            }
        } catch (IllegalStateException ignored) {
            // As long as this method is always called while the lock is held, this should not occur under
            LOG.debug("Stopwatch was already stopped; ignoring IllegalStateException thrown by stop()");
        }
        return false;  // either was not running or caught IllegalStateException
    }
}
