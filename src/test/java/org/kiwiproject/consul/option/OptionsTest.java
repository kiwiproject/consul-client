package org.kiwiproject.consul.option;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@DisplayName("Options")
class OptionsTest {

    @Nested
    class From {

        @Test
        void shouldThrowIllegalArgument_WhenGivenNullVarArgs() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Options.from((ParamAdder[]) null))
                    .withMessage("the options vararg must not be null");
        }

        @Test
        void shouldReturnEmptyMap_WhenGivenNoOptions() {
            assertThat(Options.from()).isEmpty();
        }

        @Test
        void shouldReturnEmptyMap_WhenOneNullArgument() {
            var options = Options.from((ParamAdder) null);

            assertThat(options).isEmpty();
        }

        @Test
        void shouldReturnEmptyMap_WhenAllOptionsAreNull() {
            var options = Options.from(null, null);

            assertThat(options).isEmpty();
        }

        @Test
        void shouldIncludeAllOptionsInParamAdder() {
            var queryOptions = ImmutableQueryOptions.builder()
                    .token("12345")
                    .consistencyMode(ConsistencyMode.CONSISTENT)
                    .namespace("ns1")
                    .datacenter("dc42")
                    .reason("why not?")
                    .build();

            var options = Options.from(queryOptions);

            assertThat(options).containsAllEntriesOf(queryOptions.toQuery());
        }

        @Test
        void shouldIgnoreNullParamAdders() {
            var queryOptions = ImmutableQueryOptions.builder()
                    .token("12345")
                    .consistencyMode(ConsistencyMode.CONSISTENT)
                    .namespace("ns1")
                    .datacenter("dc42")
                    .reason("why not?")
                    .build();

            var options = Options.from(null, queryOptions, null);

            assertThat(options).containsAllEntriesOf(queryOptions.toQuery());
        }
    }

    @Nested
    class OptionallyAddToMap {

        private Map<String, Object> data;
        private String key;

        @BeforeEach
        void setUp() {
            data = new HashMap<>();
            key = RandomStringUtils.secure().nextAlphabetic(10);
        }

        @Test
        void shouldDoNothing_WhenOptionalIsEmpty() {
            var value = Optional.empty();
            Options.optionallyAdd(data, key, value);
            assertThat(data).isEmpty();
        }

        @Test
        void shouldPutValueAsString_ForKey_WhenOptionalContainsValue() {
            var value = Optional.of(42);
            Options.optionallyAdd(data, key, value);
            assertThat(data).hasSize(1).containsEntry(key, "42");
        }

        @Test
        void shouldThrowUnsupportedOperationException_WhenGivenUnmodifiableMap() {
            var unmodifiableData = Map.<String, Object>of();
            var optionalValue = Optional.of(42);
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> Options.optionallyAdd(unmodifiableData, "theKey", optionalValue));
        }
    }

    @Nested
    class OptionallyAddToList {

        private List<String> data;
        private String key;

        @BeforeEach
        void setUp() {
            data = new ArrayList<>();
            key = RandomStringUtils.secure().nextAlphabetic(10);
        }

        @Test
        void shouldDoNothing_WhenOptionalIsEmpty() {
            var value = Optional.<Boolean>empty();
            Options.optionallyAdd(data, key, value);
            assertThat(data).isEmpty();
        }

        @Test
        void shouldDoNothing_WhenOptionalContainsFalseBoolean() {
            var value = Optional.of(false);
            Options.optionallyAdd(data, key, value);
            assertThat(data).isEmpty();
        }

        @Test
        void shouldAddKey_WhenOptionalContainsTrueBoolean() {
            var value = Optional.of(true);
            Options.optionallyAdd(data, key, value);
            assertThat(data).containsExactly(key);
        }

        @Test
        void shouldThrowUnsupportedOperationException_WhenGivenUnmodifiableList() {
            var unmodifiableData = List.<String>of();
            var optionalValue = Optional.of(true);
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> Options.optionallyAdd(unmodifiableData, "theKey", optionalValue));
        }
    }
}
