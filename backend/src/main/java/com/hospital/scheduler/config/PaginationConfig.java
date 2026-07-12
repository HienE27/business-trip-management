package com.hospital.scheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Centralized pagination configuration.
 *
 * Solves BUG-m1 (no size limit), BUG-m2 (negative page → 0),
 * and BUG-m3 (size=0 returns empty list instead of 400).
 */
@Configuration
@ConfigurationProperties(prefix = "app.pagination")
public class PaginationConfig {

    private int defaultPageSize = 20;
    private int maxPageSize = 100;
    private int maxPageNumber = 1000;

    public int getDefaultPageSize() {
        return defaultPageSize;
    }

    public void setDefaultPageSize(int defaultPageSize) {
        this.defaultPageSize = defaultPageSize;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    public int getMaxPageNumber() {
        return maxPageNumber;
    }

    public void setMaxPageNumber(int maxPageNumber) {
        this.maxPageNumber = maxPageNumber;
    }

    /**
     * Build a safe PageRequest with all guards applied.
     * - Negative page → 0
     * - size <= 0  → defaultPageSize
     * - size > maxPageSize → maxPageSize
     * - page > maxPageNumber → maxPageNumber
     */
    public PageRequest of(Integer page, Integer size) {
        int safePage = (page == null || page < 0) ? 0 : Math.min(page, maxPageNumber);
        int safeSize = (size == null || size <= 0) ? defaultPageSize : Math.min(size, maxPageSize);
        return PageRequest.of(safePage, safeSize);
    }

    /**
     * Build a PageRequest with a fixed sort applied.
     */
    public PageRequest of(Integer page, Integer size, org.springframework.data.domain.Sort sort) {
        int safePage = (page == null || page < 0) ? 0 : Math.min(page, maxPageNumber);
        int safeSize = (size == null || size <= 0) ? defaultPageSize : Math.min(size, maxPageSize);
        return PageRequest.of(safePage, safeSize, sort);
    }
}
