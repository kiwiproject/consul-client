package org.kiwiproject.consul.option;

import static com.google.common.base.Preconditions.checkArgument;

import org.immutables.value.Value;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Container for common query options used by the Consul API.
 */
@Value.Immutable
public abstract class QueryOptions implements ParamAdder {

    public static final QueryOptions BLANK = ImmutableQueryOptions.builder().build();

    public abstract Optional<String> getWait();
    public abstract Optional<String> getToken();
    public abstract Optional<String> getHash();
    public abstract Optional<BigInteger> getIndex();
    public abstract Optional<String> getNear();
    public abstract Optional<String> getDatacenter();
    public abstract Optional<String> getFilter();
    public abstract Optional<String> getNamespace();
    public abstract Optional<Boolean> getWan();
    public abstract Optional<String> getSegment();
    public abstract Optional<String> getNote();
    public abstract Optional<Boolean> getEnable();
    public abstract Optional<String> getReason();
    public abstract List<String> getNodeMeta();
    public abstract List<String> getTag();

    @Value.Default
    public ConsistencyMode getConsistencyMode() {
        return ConsistencyMode.DEFAULT;
    }

    @Value.Derived
    public boolean isBlocking() {
        return getWait().isPresent();
    }

    @Value.Derived
    public boolean hasToken() {
        return getToken().isPresent();
    }

    @Value.Derived
    public List<String> getNodeMetaQuery() {
        return List.copyOf(getNodeMeta());
    }

    @Value.Derived
    public List<String> getTagsQuery() {
        return List.copyOf(getTag());
    }

    @Value.Check
    void validate() {
        if (isBlocking()) {
            checkArgument(getIndex().isPresent() || getHash().isPresent(), "If wait is specified, index/hash must also be specified");
            checkArgument(!(getIndex().isPresent() && getHash().isPresent()), "Cannot specify index and hash ath the same time");
        }
    }

    public static ImmutableQueryOptions.Builder blockSeconds(int seconds, BigInteger index) {
        return blockBuilder("s", seconds, index);
    }

    public static ImmutableQueryOptions.Builder blockMinutes(int minutes, BigInteger index) {
        return blockBuilder("m", minutes, index);
    }

    private static ImmutableQueryOptions.Builder blockBuilder(String identifier, int qty, BigInteger index) {
        return ImmutableQueryOptions.builder()
                .wait(String.format("%s%s", qty, identifier))
                .index(index);
    }

    public static ImmutableQueryOptions.Builder blockSeconds(int seconds, String hash) {
        return blockBuilder("s", seconds, hash);
    }

    public static ImmutableQueryOptions.Builder blockMinutes(int minutes, String hash) {
        return blockBuilder("m", minutes, hash);
    }

    private static ImmutableQueryOptions.Builder blockBuilder(String identifier, int qty, String hash) {
        return ImmutableQueryOptions.builder()
                .wait(String.format("%s%s", qty, identifier))
                .hash(hash);
    }

    @Override
    public Map<String, Object> toQuery() {
        Map<String, Object> result = new HashMap<>();

        Optional<String> consistency = getConsistencyMode().toParam();
        consistency.ifPresent(s -> result.put(s, ""));

        if (isBlocking()) {
            Options.optionallyAdd(result, "wait", getWait());
            Options.optionallyAdd(result, "index", getIndex());
            Options.optionallyAdd(result, "hash", getHash());
        }

        Options.optionallyAdd(result, "token", getToken());
        Options.optionallyAdd(result, "near", getNear());
        Options.optionallyAdd(result, "dc", getDatacenter());
        Options.optionallyAdd(result, "filter", getFilter());
        Options.optionallyAdd(result, "ns", getNamespace());
        Options.optionallyAdd(result, "wan", getWan());
        Options.optionallyAdd(result, "segment", getSegment());
        Options.optionallyAdd(result, "note", getNote());
        Options.optionallyAdd(result, "enable", getEnable());
        Options.optionallyAdd(result, "reason", getReason());

        return result;
    }

    @Override
    public Map<String, String> toHeaders() {
        return getConsistencyMode().getAdditionalHeaders();
    }
}
