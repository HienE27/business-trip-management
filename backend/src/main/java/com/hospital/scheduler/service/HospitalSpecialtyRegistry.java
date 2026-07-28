package com.hospital.scheduler.service;

import com.hospital.scheduler.algorithm.scoring.StaffShiftTypeEligibility;
import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.repository.SpecialtyRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cung cấp danh sách active specialties của bệnh viện một cách dynamic.
 *
 * <p>Thay vì hard-code {@code Set.of("Ngoại", "Nội", ...)} trong code,
 * danh sách này được đọc từ database và cache tại field {@code cachedNames}.
 * Khi bệnh viện thêm khoa mới (Tim mạch, Da liễu, Tai Mũi Họng...), engine
 * tự động nhận biết sau khi gọi {@link #evictCache()}.
 *
 * <p>Không dùng {@code @Cacheable} vì method reference trong
 * {@link #registerProvider()} bypass Spring AOP proxy, khiến cache không生效.
 *
 * <p>Sau khi khởi tạo, service này đăng ký provider động với
 * {@link StaffShiftTypeEligibility} để mọi eligibility check dùng danh sách
 * thực từ database thay vì default 6 khoa.
 *
 * @see StaffShiftTypeEligibility
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HospitalSpecialtyRegistry {

    private final SpecialtyRepository specialtyRepository;

    /** Cache in-memory thay vì @Cacheable (bị bypass bởi method reference). */
    private volatile Set<String> cachedNames = null;

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
     * <p>Kết quả cached tại field {@code cachedNames}. Khi admin thêm/sửa/xóa specialty,
     * gọi {@link #evictCache()} để refresh.
     *
     * @return Immutable set of active specialty names (e.g. {"Ngoại", "Nội", "Sản", "Nhi", "Mắt", "Răng"})
     */
    public Set<String> getAllActiveSpecialtyNames() {
        if (cachedNames != null) return cachedNames;
        log.debug("Loading active specialties from database...");
        Set<String> names = specialtyRepository.findByIsActiveTrue()
                .stream()
                .map(Specialty::getName)
                .collect(Collectors.collectingAndThen(Collectors.toSet(), Collections::unmodifiableSet));
        cachedNames = names;
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
    public void evictCache() {
        cachedNames = null;
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
