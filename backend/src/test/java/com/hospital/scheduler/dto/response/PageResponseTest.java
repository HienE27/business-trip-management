package com.hospital.scheduler.dto.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the canonical pagination envelope used by PR-11-03.
 */
@DisplayName("PageResponse — canonical pagination wrapper (PR-11-03)")
class PageResponseTest {

    @Test
    @DisplayName("Maps Page<Entity> → PageResponse<DTO> with all flags set")
    void fromPage_mapsAllFields() {
        PageImpl<String> page = new PageImpl<>(
                List.of("a", "b", "c"),
                PageRequest.of(1, 3, Sort.by(Sort.Direction.DESC, "createdAt")),
                9);

        PageResponse<Integer> result = PageResponse.from(page, String::length);

        assertThat(result.items()).containsExactly(1, 1, 1);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(3);
        assertThat(result.totalItems()).isEqualTo(9);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.hasPrev()).isTrue();
        assertThat(result.sort()).contains("createdAt");
    }

    @Test
    @DisplayName("First page (no prev, has next when total > size)")
    void firstPage_noPrev() {
        PageImpl<String> page = new PageImpl<>(
                List.of("only"), PageRequest.of(0, 5), 1);

        PageResponse<String> result = PageResponse.from(page, s -> s);

        assertThat(result.hasPrev()).isFalse();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("Unsorted page returns null sort field")
    void unsortedPage_nullSort() {
        PageImpl<String> page = new PageImpl<>(List.of("x"), PageRequest.of(0, 5), 1);

        PageResponse<String> result = PageResponse.from(page, s -> s);

        assertThat(result.sort()).isNull();
    }
}