package org.kiwiproject.consul.model.kv;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.math.BigInteger;
import java.util.Optional;

@Value.Immutable
@Value.Style(jakarta = true)
@JsonDeserialize(as = ImmutableTxError.class)
@JsonSerialize(as = ImmutableTxError.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class TxError {

    @JsonProperty("OpIndex")
    public abstract Optional<BigInteger> opIndex();

    @JsonProperty("What")
    public abstract Optional<String> what();
}
