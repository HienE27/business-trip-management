package com.hospital.scheduler.service;

import com.hospital.scheduler.algorithm.scoring.StaffShiftTypeEligibility;
import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.repository.SpecialtyRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cung cấp danh sách active specialties của bệnh viện một cách dynamic.
 *
 * <p>Thay vì hard-code {@code Set.of("Ngoại", "Nội", ...)} trong code,
 * danh sách này được đọc từ database mỗi khi có thay đổi (qua cache eviction).
 * Khi bệnh viện thêm khoa mới (Tim mạch, Da liễu, Tai Mũi Họng...), engine
 * tự động nhận biết mà không cần sửa code.
 *
 * <p>Sau khi khởi tạo, service này đăng ký provider động với
 * {@link StaffShiftTypeEligibility} để mọi eligibility check dùng danh sách
 * thực từ database thay vì default 6 khoa.
 *
 * <p>Cache key: {@code hospital-eligible-specialties}.
 * Evict khi: {@link #evictCache()} được gọi (sau khi thêm/sửa/xóa specialty).
 *
 * @see StaffShiftTypeEligibility
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HospitalSpecialtyRegistry {

    private static final String CACHE_NAME = "hospital-eligible-specialties";

    private final SpecialtyRepository specialtyRepository;

    /**
     * Inject dynamic provider vào StaffShiftTypeEligibility sau khi bean được khởi tạo.
     * Đảm bảo engine dùng danh sách thực từ DB thay vì default 6 khoa.
     */
    @PostConstruct
    public void registerProvider() {
        log.info("Registering dynamic specialty provider with StaffShiftTypeEligibility...");
        StaffShiftTypeEligibility.setSpecialtyProvider(this::getAllActiveSpecialtyNames);
        Set<String> names = getAllActiveSpecialtyNames();
        log.info("Dynamic specialty provider registered with {} specialties: {}", names.size(), names);
    }

    /**
     * Trả về tập hợp tên tất cả specialties active trong hệ thống.
     *
     * <p>Kết quả được cached. Khi admin thêm/sửa/xóa specialty,
     * gọi {@link #evictCache()} để refresh.
     *
     * @return Immutable set of active specialty names (e.g. {"Ngoại", "Nội", "Sản", "Nhi", "Mắt", "Răng"})
     */
    @Cacheable(value = CACHE_NAME, unless = "#result == null || #result.isEmpty()")
    public Set<String> getAllActiveSpecialtyNames() {
        log.debug("Loading active specialties from database...");
        Set<String> names = specialtyRepository.findByIsActiveTrue()
                .stream()
                .map(Specialty::getName)
                .collect(Collectors.collectingAndThen(Collectors.toSet(), Collections::unmodifiableSet));
        log.info("Loaded {} active specialties: {}", names.size(), names);
        return names;
    }

    /**
     * Xóa cache để buộc reload danh sách specialties vào lần gọi tiếp theo.
     *
     * <p>Gọi phương thức này sau khi:
     * <ul>
     *   <li>Thêm specialty mới</li>
     *   <li>Sửa tên specialty</li>
     *   <li>Deactivate specialty</li>
     *   <li>Reactivate specialty</li>
     * </ul>
     */
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void evictCache() {
        log.info("Evicting hospital-eligible-specialties cache. Next call will reload from DB.");
        // Re-register provider với danh sách mới
        StaffShiftTypeEligibility.setSpecialtyProvider(this::getAllActiveSpecialtyNames);
    }

    /**
     * Kiểm tra xem specialty name có trong danh sách active không.
     *
     * @param specialtyName tên specialty cần kiểm tra
     * @return true nếu tồn tại và active
     */
    public boolean isActiveSpecialty(String specialtyName) {
        if (specialtyName == null || specialtyName.isBlank()) return false;
        return getAllActiveSpecialtyNames().contains(specialtyName);
    }
}
