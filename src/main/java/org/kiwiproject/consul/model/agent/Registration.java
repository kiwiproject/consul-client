package org.kiwiproject.consul.model.agent;

import static com.google.common.base.Preconditions.checkState;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;
import org.kiwiproject.consul.model.catalog.ServiceWeights;

import java.util.List;
import java.util.Map;
import java.util.Optional;


@Value.Immutable
@Value.Style(jakarta = true)
@JsonSerialize(as = ImmutableRegistration.class)
@JsonDeserialize(as = ImmutableRegistration.class)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class Registration {

    @JsonProperty("Name")
    public abstract String getName();

    @JsonProperty("Id")
    public abstract String getId();

    @JsonProperty("Address")
    public abstract Optional<String> getAddress();

    @JsonProperty("Port")
    public abstract Optional<Integer> getPort();

    @JsonProperty("Check")
    public abstract Optional<RegCheck> getCheck();

    @JsonProperty("Checks")
    public abstract List<RegCheck> getChecks();

    @JsonProperty("Tags")
    public abstract List<String> getTags();

    @JsonProperty("Meta")
    public abstract Map<String,String> getMeta();

    @JsonProperty("EnableTagOverride")
    public abstract Optional<Boolean> getEnableTagOverride();

    @JsonProperty("Weights")
    public abstract Optional<ServiceWeights> getServiceWeights();

    @Value.Immutable
    @JsonSerialize(as = ImmutableRegCheck.class)
    @JsonDeserialize(as = ImmutableRegCheck.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public abstract static class RegCheck {

        @JsonProperty("CheckID")
        public abstract Optional<String> getId();

        @JsonProperty("Name")
        public abstract Optional<String> getName();

        @JsonProperty("Args")
        public abstract Optional<List<String>> getArgs();

        @JsonProperty("Interval")
        public abstract Optional<String> getInterval();

        @JsonProperty("TTL")
        public abstract Optional<String> getTtl();

        @JsonProperty("HTTP")
        public abstract Optional<String> getHttp();

        @JsonProperty("TCP")
        public abstract Optional<String> getTcp();

        @JsonProperty("GRPC")
        public abstract Optional<String> getGrpc();

        @JsonProperty("GRPCUseTLS")
        public abstract Optional<Boolean> getGrpcUseTls();

        @JsonProperty("Timeout")
        public abstract Optional<String> getTimeout();

        @JsonProperty("Notes")
        public abstract Optional<String> getNotes();

        @JsonProperty("DeregisterCriticalServiceAfter")
        public abstract Optional<String> getDeregisterCriticalServiceAfter();

        @JsonProperty("TLSSkipVerify")
        public abstract Optional<Boolean> getTlsSkipVerify();

        @JsonProperty("Status")
        public abstract Optional<String> getStatus();

        @JsonProperty("SuccessBeforePassing")
        public abstract Optional<Integer> getSuccessBeforePassing();

        @JsonProperty("FailuresBeforeCritical")
        public abstract Optional<Integer> getFailuresBeforeCritical();

        public static RegCheck ttl(long ttl) {
            return ImmutableRegCheck
                    .builder()
                    .ttl(String.format("%ss", ttl))
                    .build();
        }

        public static RegCheck args(List<String> args, long interval) {
            return ImmutableRegCheck
                    .builder()
                    .args(args)
                    .interval(String.format("%ss", interval))
                    .build();
        }

        public static RegCheck args(List<String> args, long interval, long timeout) {
            return ImmutableRegCheck
                    .builder()
                    .args(args)
                    .interval(String.format("%ss", interval))
                    .timeout(String.format("%ss", timeout))
                    .build();
        }

        public static RegCheck args(List<String> args, long interval, long timeout, String notes) {
            return ImmutableRegCheck
                    .builder()
                    .args(args)
                    .interval(String.format("%ss", interval))
                    .timeout(String.format("%ss", timeout))
                    .notes(notes)
                    .build();
        }

        public static RegCheck http(String http, long interval) {
            return ImmutableRegCheck
                    .builder()
                    .http(http)
                    .interval(String.format("%ss", interval))
                    .build();
        }

        public static RegCheck http(String http, long interval, long timeout) {
            return ImmutableRegCheck
                    .builder()
                    .http(http)
                    .interval(String.format("%ss", interval))
                    .timeout(String.format("%ss", timeout))
                    .build();
        }

        public static RegCheck http(String http, long interval, long timeout, String notes) {
            return ImmutableRegCheck
                    .builder()
                    .http(http)
                    .interval(String.format("%ss", interval))
                    .timeout(String.format("%ss", timeout))
                    .notes(notes)
                    .build();
        }

        public static RegCheck tcp(String tcp, long interval) {
            return ImmutableRegCheck
                    .builder()
                    .tcp(tcp)
                    .interval(String.format("%ss", interval))
                    .build();
        }

        public static RegCheck tcp(String tcp, long interval, long timeout) {
            return ImmutableRegCheck
                    .builder()
                    .tcp(tcp)
                    .interval(String.format("%ss", interval))
                    .timeout(String.format("%ss", timeout))
                    .build();
        }

        public static RegCheck tcp(String tcp, long interval, long timeout, String notes) {
            return ImmutableRegCheck
                    .builder()
                    .tcp(tcp)
                    .interval(String.format("%ss", interval))
                    .timeout(String.format("%ss", timeout))
                    .notes(notes)
                    .build();
        }

        public static RegCheck grpc(String grpc, long interval) {
            return RegCheck.grpc(grpc, interval, false);
        }

        public static RegCheck grpc(String grpc, long interval, boolean useTls) {
            return ImmutableRegCheck
                    .builder()
                    .grpc(grpc)
                    .grpcUseTls(useTls)
                    .interval(String.format("%ss", interval))
                    .build();
        }

        @Value.Check
        protected void validate() {

            checkState(getHttp().isPresent() || getTtl().isPresent()
                || getArgs().isPresent() || getTcp().isPresent() || getGrpc().isPresent(),
                    "Check must specify either http, tcp, ttl, grpc or args");

            if (getHttp().isPresent() || getArgs().isPresent() || getTcp().isPresent() || getGrpc().isPresent()) {
                checkState(getInterval().isPresent(),
                        "Interval must be set if check type is http, tcp, grpc or args");
            }
        }

    }

}
