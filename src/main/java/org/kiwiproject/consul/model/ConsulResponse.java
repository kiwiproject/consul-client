package org.kiwiproject.consul.model;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;

import java.math.BigInteger;
import java.util.Optional;

public class ConsulResponse<T> {

    public interface CacheResponseInfo {
        /**
         * This value can be null if value is not in cache
         *
         * @return an Optional containing the age in seconds, or an empty Optional
         */
        Optional<Long> getAgeInSeconds();

        boolean isCacheHit();
    }

    private static class CacheResponseInfoImpl implements CacheResponseInfo {
        private final Long ageInSeconds;
        private final boolean cacheHit;
        public CacheResponseInfoImpl(String headerHitMiss, String headerAge) throws NumberFormatException {
            this.cacheHit = headerHitMiss.equals("HIT");
            Long val = null;
            if (nonNull(headerAge)) {
                val = Long.parseLong(headerAge);
            }
            this.ageInSeconds = val;
        }

        @Override
        public Optional<Long> getAgeInSeconds() {
            return Optional.ofNullable(ageInSeconds);
        }

        @Override
        public boolean isCacheHit() {
            return cacheHit;
        }

        @Override
        public String toString(){
            return String.format("Cache[%s, age=%d]",
                                 cacheHit ? "HIT" : "MISS",
                                 ageInSeconds);
        }
    }

    private final T response;
    private final long lastContact;
    private final boolean knownLeader;
    private final BigInteger index;
    private final Optional<CacheResponseInfo> cacheResponseInfo;

    @VisibleForTesting
    static CacheResponseInfo buildCacheResponseInfo(String headerHitMiss, String headerAge) throws NumberFormatException {
        ConsulResponse.CacheResponseInfo cacheInfo = null;
        if (nonNull(headerHitMiss)) {
            cacheInfo = new CacheResponseInfoImpl(headerHitMiss, headerAge);
        }
        return cacheInfo;
    }

    public ConsulResponse(T response, long lastContact, boolean knownLeader, BigInteger index, String headerHitMiss, String headerAge) throws NumberFormatException {
        this(response, lastContact, knownLeader, index, Optional.ofNullable(buildCacheResponseInfo(headerHitMiss, headerAge)));
    }

    public ConsulResponse(T response, long lastContact, boolean knownLeader, BigInteger index, Optional<CacheResponseInfo> cacheInfo) {
        this.response = response;
        this.lastContact = lastContact;
        this.knownLeader = knownLeader;
        this.index = index;
        this.cacheResponseInfo = cacheInfo;
    }

    public T getResponse() {
        return response;
    }

    public long getLastContact() {
        return lastContact;
    }

    public boolean isKnownLeader() {
        return knownLeader;
    }

    public BigInteger getIndex() {
        return index;
    }

    /**
     * @return an Optional containing the CacheResponseInfo, or empty Optional if it does not exist
     * @deprecated replaced by {@link #getCacheResponseInfo()}
     * @see <a href="https://developer.hashicorp.com/consul/api-docs/features/caching#background-refresh-caching">Background Refresh Caching</a>
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
    public Optional<CacheResponseInfo> getCacheReponseInfo(){
        return getCacheResponseInfo();
    }

    /**
     * @return an Optional containing the CacheResponseInfo, or empty Optional if it does not exist
     * @see <a href="https://developer.hashicorp.com/consul/api-docs/features/caching#background-refresh-caching">Background Refresh Caching</a>
     */
    public Optional<CacheResponseInfo> getCacheResponseInfo() {
        return cacheResponseInfo;
    }

    @Override
    public String toString() {
        return "ConsulResponse{" +
                "response=" + response +
                ", lastContact=" + lastContact +
                ", knownLeader=" + knownLeader +
                ", index=" + index +
                ", cache=" + cacheResponseInfo +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (isNull(o) || getClass() != o.getClass()) return false;

        ConsulResponse<?> that = (ConsulResponse<?>) o;

        return Objects.equal(this.response, that.response) &&
                Objects.equal(this.lastContact, that.lastContact) &&
                Objects.equal(this.knownLeader, that.knownLeader) &&
                Objects.equal(this.index, that.index);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(response, lastContact, knownLeader, index);
    }
}
