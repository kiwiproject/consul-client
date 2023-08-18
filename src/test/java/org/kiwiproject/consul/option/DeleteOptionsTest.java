package org.kiwiproject.consul.option;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("DeleteOptions")
class DeleteOptionsTest {

    @Nested
    class IsRecurse {

        @Test
        void shouldReturnFalse_WhenGetRecurseReturnsEmptyOptional() {
            var deleteOptions = ImmutableDeleteOptions.builder().build();
            assertThat(deleteOptions.getRecurse()).isEmpty();
            assertThat(deleteOptions.isRecurse()).isFalse();
        }

        @ParameterizedTest
        @ValueSource(booleans = { true, false })
        void shouldReturnExpectedValue_WhenGetRecurseReturnsOptionalWithValue(boolean recurse) {
            var deleteOptions = ImmutableDeleteOptions.builder().recurse(recurse).build();
            assertThat(deleteOptions.getRecurse()).contains(recurse);
            assertThat(deleteOptions.isRecurse()).isEqualTo(recurse);
        }
    }
}
