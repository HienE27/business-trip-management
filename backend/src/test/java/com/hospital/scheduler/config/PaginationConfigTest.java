package com.hospital.scheduler.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PaginationConfig — validates BUG-m1, BUG-m2, BUG-m3 fixes.
 *
 * - BUG-m1: size exceeding max → capped to maxPageSize
 * - BUG-m2: negative page → coerced to 0
 * - BUG-m3: size=0 returns empty list → coerced to defaultPageSize
 */
@DisplayName("PaginationConfig - Pagination limit guards")
class PaginationConfigTest {

    private PaginationConfig config;

    @BeforeEach
    void setUp() {
        config = new PaginationConfig();
        config.setDefaultPageSize(20);
        config.setMaxPageSize(100);
        config.setMaxPageNumber(1000);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // BUG-m2: negative page → 0
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("BUG-m2: Negative page number → 0")
    class NegativePage {

        @Test
        void nullPage_defaultsToZero() {
            PageRequest pr = config.of(null, 10);
            assertThat(pr.getPageNumber()).isEqualTo(0);
        }

        @Test
        void negativePage_coercedToZero() {
            PageRequest pr = config.of(-5, 10);
            assertThat(pr.getPageNumber()).isEqualTo(0);
        }

        @Test
        void negativePageMinusOne_coercedToZero() {
            PageRequest pr = config.of(-1, 10);
            assertThat(pr.getPageNumber()).isEqualTo(0);
        }

        @Test
        void pageAboveMaxPageNumber_capped() {
            PageRequest pr = config.of(5000, 10);
            assertThat(pr.getPageNumber()).isEqualTo(1000); // max page number
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // BUG-m3: size=0 → defaultPageSize
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("BUG-m3: size=0 or negative → defaultPageSize (20)")
    class ZeroOrNegativeSize {

        @Test
        void nullSize_defaultsToDefaultPageSize() {
            PageRequest pr = config.of(0, null);
            assertThat(pr.getPageSize()).isEqualTo(20); // defaultPageSize
        }

        @Test
        void zeroSize_coercedToDefaultPageSize() {
            PageRequest pr = config.of(0, 0);
            assertThat(pr.getPageSize()).isEqualTo(20); // defaultPageSize
        }

        @Test
        void negativeSize_coercedToDefaultPageSize() {
            PageRequest pr = config.of(0, -10);
            assertThat(pr.getPageSize()).isEqualTo(20); // defaultPageSize
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // BUG-m1: size exceeding max → maxPageSize
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("BUG-m1: size exceeding max → maxPageSize (100)")
    class OversizedPage {

        @Test
        void sizeAboveMax_cappedTo100() {
            PageRequest pr = config.of(0, 500);
            assertThat(pr.getPageSize()).isEqualTo(100); // maxPageSize
        }

        @Test
        void sizeWayAboveMax_cappedTo100() {
            PageRequest pr = config.of(0, 999999);
            assertThat(pr.getPageSize()).isEqualTo(100);
        }

        @Test
        void sizeAtMax_exact100() {
            PageRequest pr = config.of(0, 100);
            assertThat(pr.getPageSize()).isEqualTo(100);
        }

        @Test
        void sizeBelowMax_unchanged() {
            PageRequest pr = config.of(0, 50);
            assertThat(pr.getPageSize()).isEqualTo(50);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Sort preservation
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Sort preservation")
    class SortPreservation {

        @Test
        void sortIsPreserved() {
            Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
            PageRequest pr = config.of(2, 50, sort);

            assertThat(pr.getPageNumber()).isEqualTo(2);
            assertThat(pr.getPageSize()).isEqualTo(50);
            assertThat(pr.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        }

        @Test
        void invalidPageAndSize_stillSafe() {
            PageRequest pr = config.of(-1, -999, Sort.by("name"));

            assertThat(pr.getPageNumber()).isEqualTo(0);
            assertThat(pr.getPageSize()).isEqualTo(20);
        }
    }
}
