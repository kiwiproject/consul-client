package com.orbitz.consul.option;

import static com.orbitz.consul.TestUtils.randomUUIDString;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("QueryOptions")
class QueryOptionsTest {

    @Nested
    class GetNodeMetaQuery {

        @Test
        void shouldReturnEmptyList_WhenNoMetaAdded() {
            var queryOptions = ImmutableQueryOptions.builder().build();

            assertThat(queryOptions.getNodeMetaQuery())
                    .isUnmodifiable()
                    .isEmpty();
        }

        @Test
        void shouldReturnMeta_WhenMetaAdded() {
            var meta1 = randomUUIDString();
            var meta2 = randomUUIDString();
            var queryOptions = ImmutableQueryOptions.builder()
                    .addNodeMeta(meta1)
                    .addNodeMeta(meta2)
                    .build();

            assertThat(queryOptions.getNodeMetaQuery())
                    .isUnmodifiable()
                    .containsExactly(meta1, meta2);
        }

    }

    @Nested
    class GetTagsQuery {

        @Test
        void shouldReturnEmptyList_WhenNoTagsAdded() {
            var queryOptions = ImmutableQueryOptions.builder().build();

            assertThat(queryOptions.getTagsQuery())
                    .isUnmodifiable()
                    .isEmpty();
        }

        @Test
        void shouldReturnTags_WhenTagsAdded() {
            var tag1 = randomUUIDString();
            var tag2 = randomUUIDString();
            var tag3 = randomUUIDString();
            var queryOptions = ImmutableQueryOptions.builder()
                    .addTag(tag1)
                    .addTag(tag2)
                    .addTag(tag3)
                    .build();

            assertThat(queryOptions.getTagsQuery())
                    .isUnmodifiable()
                    .containsExactly(tag1, tag2, tag3);
        }
    }
}
