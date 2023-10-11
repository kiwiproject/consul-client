package org.kiwiproject.consul.model.kv;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Style;

import java.util.List;
import java.util.Map;

@Immutable
@Style(jakarta = true)
@JsonDeserialize(as = ImmutableTxResponse.class)
@JsonSerialize(as = ImmutableTxResponse.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class TxResponse {

    @JsonProperty("Results")
    public abstract List<Map<String, Value>> results();

    @JsonProperty("Errors")
    public abstract List<TxError> errors();
}
