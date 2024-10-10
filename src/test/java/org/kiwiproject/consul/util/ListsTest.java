package org.kiwiproject.consul.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.List;

@DisplayName("Lists")
class ListsTest {

    @Nested
    class FirstValueOrEmpty {

        @ParameterizedTest
        @NullAndEmptySource
        void shouldReturnEmptyOptional_WhenListIsNullOrEmpty(List<String> list) {
            assertThat(Lists.firstValueOrEmpty(list)).isEmpty();
        }

        @Test
        void shouldReturnOnlyValue_InSingleValuedList() {
            var list = List.of(42);
            assertThat(Lists.firstValueOrEmpty(list)).contains(42);
        }

        @Test
        void shouldReturnFirstValue_InMultiValuedList() {
            var list = List.of(10, 9, 8, 7);
            assertThat(Lists.firstValueOrEmpty(list)).contains(10);
        }
    }

    @Nested
    class IsNullOrEmpty {

        @ParameterizedTest
        @NullAndEmptySource
        void shouldReturnTrue_WhenListIsNullOrEmpty(List<String> list) {
            assertThat(Lists.isNullOrEmpty(list)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = { 1, 2, 3 })
        void shouldReturnFalse_WhenListIsNotEmpty(int size) {
            List<Integer> list = Collections.nCopies(size, 42);
            assertThat(Lists.isNullOrEmpty(list)).isFalse();
        }
    }
}
