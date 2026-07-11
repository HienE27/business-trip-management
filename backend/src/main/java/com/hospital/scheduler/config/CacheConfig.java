package com.hospital.scheduler.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String DASHBOARD_STATS_CACHE = "dashboardStats";
    public static final String SHIFT_TYPES_CACHE = "shiftTypes";
    public static final String SPECIALTIES_CACHE = "specialties";
    public static final String PERIODS_CACHE = "periods";
    public static final String REQUIREMENTS_CACHE = "requirements";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(caffeineCacheBuilder());
        return cacheManager;
    }

    private Caffeine<Object, Object> caffeineCacheBuilder() {
        // Bug-m4 mitigation: dashboard queries are heavy (multiple joins,
        // COUNT/FROM aggregations across schedule + staff + period tables).
        // - expireAfterWrite 5min: short enough that mutations surface quickly
        //   once an @CacheEvict is missed (extra safety beyond the existing
        //   per-service @CacheEvict annotations).
        // - recordStats: lets Prometheus/micrometer expose hit-rate metrics
        //   so we can tune if hit-rate is still low under prod load.
        return Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(500)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats();
    }
}
