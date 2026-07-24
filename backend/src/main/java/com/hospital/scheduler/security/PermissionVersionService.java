package com.hospital.scheduler.security;

import com.hospital.scheduler.entity.AlgorithmConfig;
import com.hospital.scheduler.repository.AlgorithmConfigRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks the "permission matrix version" timestamp so JWTs that were issued
 * before a permission change can be detected and invalidated.
 *
 * <p>The version is stored as an epoch-millisecond long in the
 * {@code permissions.version} row of {@code algorithm_config} and mirrored
 * in memory via an {@link AtomicLong} so every check inside the same JVM
 * is consistent and monotonic — even when multiple {@link #bump()} calls
 * happen within the same wall-clock millisecond.
 *
 * <p>Flow:
 * <ol>
 *   <li>Every JWT issued by {@link JwtService} writes the current version
 *       into the {@code permVer} claim.</li>
 *   <li>{@link com.hospital.scheduler.security.PermissionInvalidationFilter}
 *       compares that claim with {@link #currentVersion()} on every request.</li>
 *   <li>When the matrix changes (a permission is added/removed/toggled),
 *       {@link #bump()} updates the in-memory counter and the DB row, which
 *       invalidates every outstanding JWT whose {@code permVer} is now
 *       strictly less than the current version.</li>
 * </ol>
 *
 * <p>Users whose tokens become stale get a {@code 401 PERMISSION_VERSION_STALE}
 * response, which the frontend interceptor turns into a forced re-login.
 */
@Service
@RequiredArgsConstructor
public class PermissionVersionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionVersionService.class);

    public static final String KEY = "permissions.version";

    private final AlgorithmConfigRepository algorithmConfigRepository;

    // BUGFIX (was PERM-VER-LOOP): in-memory monotonic counter. Without this,
    // two bump() calls inside the same MySQL DATETIME second collapsed to the
    // same value (DATETIME precision = 1s), causing issued JWTs to carry a
    // permVer that never exceeded currentVersion() and triggering the
    // "Stale permission matrix version" WARN spam. The DB row persists the
    // version across restarts; the AtomicLong is the authoritative value for
    // in-process comparisons and ALWAYS increases on bump().
    // Both fields are static so they are properly isolated per-test when
    // JUnit creates a fresh service instance per test method (the default
    // PER_METHOD lifecycle shares the class instance across methods, meaning
    // any non-static instance field from a prior test could pollute the next).
    private static final AtomicLong cachedVersion = new AtomicLong(0L);
    private static volatile boolean cacheLoaded = false;

    /**
     * Returns the current permission matrix version as epoch milliseconds.
     * Result is guaranteed to be {@code >=} the value any previously-issued
     * JWT carries in its {@code permVer} claim.
     *
     * <p>First call loads the value from the DB; subsequent calls hit the
     * {@link AtomicLong} directly to avoid a SELECT on every authenticated
     * request.
     */
    public long currentVersion() {
        if (!cacheLoaded) {
            synchronized (cachedVersion) {
                if (!cacheLoaded) {
                    long initial = algorithmConfigRepository.findById(KEY)
                            .map(cfg -> parseEpochMs(cfg.getParamValue()))
                            .orElse(0L);
                    cachedVersion.set(initial);
                    cacheLoaded = true;
                    log.info("PermissionVersionService initialized at version {}", initial);
                }
            }
        }
        return cachedVersion.get();
    }

    /**
     * Advance the version. Returns the new value so callers (e.g. logging,
     * audit) can record it without a second read.
     *
     * <p>Monotonicity is enforced even when {@link System#currentTimeMillis()}
     * hasn't advanced (e.g. a permission toggle batched in the same
     * millisecond). The next version is always strictly greater than the
     * previous one.
     */
    @Transactional
    public long bump() {
        long next = Math.max(System.currentTimeMillis(), cachedVersion.get() + 1);
        cachedVersion.set(next);

        AlgorithmConfig cfg = algorithmConfigRepository.findById(KEY)
                .orElse(AlgorithmConfig.builder()
                        .paramKey(KEY)
                        .paramValue(String.valueOf(next))
                        .valueType(AlgorithmConfig.ValueType.STRING)
                        .description("Phiên bản cấu hình phân quyền (epoch ms). Tự động tăng khi bảng role_permission thay đổi; dùng để phát hiện và vô hiệu hóa JWT đã cũ.")
                        .build());
        cfg.setParamValue(String.valueOf(next));
        algorithmConfigRepository.save(cfg);
        log.info("Permission matrix version bumped to {}", next);
        return next;
    }

    private long parseEpochMs(String raw) {
        if (raw == null || raw.isBlank()) return 0L;
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("permissions.version param_value '{}' is not a valid epoch ms; defaulting to 0", raw);
            return 0L;
        }
    }

    /**
     * Resets the in-memory cache so tests get a clean state.
     * NOT for production use — only for unit-test isolation.
     */
    static void resetForTest() {
        cachedVersion.set(0L);
        cacheLoaded = false;
    }
}
