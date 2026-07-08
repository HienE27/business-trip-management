package com.hospital.scheduler.service;

import com.hospital.scheduler.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Centralized cache invalidation. Any service that mutates data which feeds the
 * dashboard summary (staff count, period count, schedule count, leave/exchange counts)
 * MUST call {@link #evictDashboard()} after committing the change. Otherwise the
 * Caffeine TTL (10 minutes by default) will keep stale numbers on screen.
 *
 * Programmatic eviction is preferred over {@code @CacheEvict} on every service
 * method because:
 * - One place to log/monitor evictions.
 * - Easier to extend (add new caches) without touching every service.
 * - Caller doesn't need to know cache names.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheEvictor {

    private final CacheManager cacheManager;

    /**
     * Evict every entry in the dashboard stats cache. Called after any mutation
     * that affects dashboard counters (staff create/update/delete, period CRUD,
     * leave request state change, schedule CRUD, etc.).
     */
    public void evictDashboard() {
        evict(CacheConfig.DASHBOARD_STATS_CACHE, "all");
    }

    /**
     * Evict every entry across all caches. Use sparingly — e.g. after bulk imports
     * that affect multiple data dimensions at once.
     */
    public void evictAll() {
        for (String name : cacheManager.getCacheNames()) {
            evict(name, "all");
        }
    }

    private void evict(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            // Cache not configured — silently no-op so services don't need to know
            // which caches exist. Log at DEBUG to keep production logs quiet.
            log.debug("Cache '{}' is not configured — skipping evict", cacheName);
            return;
        }
        try {
            if ("all".equals(key)) {
                cache.clear();
            } else {
                cache.evict(key);
            }
        } catch (Exception ex) {
            // Never let cache eviction failures break a successful write. Log and move on.
            log.warn("Failed to evict cache '{}' key='{}': {}", cacheName, key, ex.getMessage());
        }
    }
}