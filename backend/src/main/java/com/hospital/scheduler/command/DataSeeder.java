package com.hospital.scheduler.command;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Transactional
public class DataSeeder implements CommandLineRunner {

    private final AppRoleRepository appRoleRepository;
    private final StaffRepository staffRepository;
    private final SpecialtyRepository specialtyRepository;
    private final ShiftTypeRepository shiftTypeRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedRoles();
        seedSpecialties();
        seedShiftTypes();
        seedAdminUser();
    }

    private void seedRoles() {
        if (appRoleRepository.count() > 0) return;

        appRoleRepository.save(AppRole.builder().name("ADMIN").description("Quản trị hệ thống").isActive(true).build());
        appRoleRepository.save(AppRole.builder().name("MANAGER").description("Quản lý và duyệt lịch").isActive(true).build());
        appRoleRepository.save(AppRole.builder().name("STAFF").description("Nhân viên sử dụng hệ thống").isActive(true).build());

        System.out.println("✅ Seeded roles: ADMIN, MANAGER, STAFF");
    }

    private void seedSpecialties() {
        if (specialtyRepository.count() > 0) return;

        specialtyRepository.save(Specialty.builder().name("Bác sĩ").description("Bác sĩ chuyên khoa").isActive(true).build());
        specialtyRepository.save(Specialty.builder().name("Điều dưỡng").description("Điều dưỡng viên").isActive(true).build());
        specialtyRepository.save(Specialty.builder().name("Kỹ thuật viên").description("Kỹ thuật viên xét nghiệm").isActive(true).build());
        specialtyRepository.save(Specialty.builder().name("Dược sĩ").description("Dược sĩ bệnh viện").isActive(true).build());

        System.out.println("✅ Seeded specialties");
    }

    private void seedShiftTypes() {
        if (shiftTypeRepository.count() > 0) return;

        shiftTypeRepository.save(ShiftType.builder()
                .id("L01").name("Lịch trực 24/24")
                .description("Ca trực 24/24 từ 7h30 ngày N đến 7h30 ngày N+1, có nghỉ bù")
                .isOvernight(true).fatigueScore(3).isActive(true).build());

        shiftTypeRepository.save(ShiftType.builder()
                .id("L02").name("Lịch thông tầm")
                .description("Ca ngày, không nghỉ trưa")
                .isOvernight(false).fatigueScore(1).isActive(true).build());

        shiftTypeRepository.save(ShiftType.builder()
                .id("L03").name("Lịch phòng khám dịch vụ")
                .description("Ca khám dịch vụ")
                .isOvernight(false).fatigueScore(1).isActive(true).build());

        shiftTypeRepository.save(ShiftType.builder()
                .id("L04").name("Lịch phòng khám chuyên gia")
                .description("Ca khám chuyên sâu")
                .isOvernight(false).fatigueScore(2).isActive(true).build());

        System.out.println("✅ Seeded shift types: L01, L02, L03, L04");
    }

    private void seedAdminUser() {
        if (staffRepository.count() > 0) return;

        AppRole adminRole = appRoleRepository.findByName("ADMIN").orElse(null);
        AppRole managerRole = appRoleRepository.findByName("MANAGER").orElse(null);
        Specialty doctorSpecialty = specialtyRepository.findByName("Bác sĩ").orElse(null);

        Staff admin = Staff.builder()
                .username("admin")
                .passwordHash(passwordEncoder.encode("admin123"))
                .fullName("Quản trị viên")
                .phone("0901234567")
                .email("admin@hospital.com")
                .specialty(doctorSpecialty)
                .maxShiftsPerMonth(5)
                .isActive(true)
                .staffRoles(new HashSet<>())
                .build();

        Staff savedAdmin = staffRepository.save(admin);

        if (adminRole != null) {
            StaffRole ar = StaffRole.builder().staffId(savedAdmin.getId()).roleId(adminRole.getId()).build();
            savedAdmin.getStaffRoles().add(ar);
        }
        if (managerRole != null) {
            StaffRole mr = StaffRole.builder().staffId(savedAdmin.getId()).roleId(managerRole.getId()).build();
            savedAdmin.getStaffRoles().add(mr);
        }
        staffRepository.save(savedAdmin);

        // Create some sample staff
        Specialty nurseSpecialty = specialtyRepository.findByName("Điều dưỡng").orElse(null);
        AppRole staffRole = appRoleRepository.findByName("STAFF").orElse(null);

        for (int i = 1; i <= 5; i++) {
            Staff staff = Staff.builder()
                    .username("staff" + i)
                    .passwordHash(passwordEncoder.encode("123456"))
                    .fullName("Nhân viên " + i)
                    .phone("090" + String.format("%06d", i * 1111))
                    .email("staff" + i + "@hospital.com")
                    .specialty(i % 2 == 0 ? doctorSpecialty : nurseSpecialty)
                    .maxShiftsPerMonth(5)
                    .isActive(true)
                    .staffRoles(new HashSet<>())
                    .build();

            Staff savedStaff = staffRepository.save(staff);
            if (staffRole != null) {
                StaffRole sr = StaffRole.builder().staffId(savedStaff.getId()).roleId(staffRole.getId()).build();
                savedStaff.getStaffRoles().add(sr);
                staffRepository.save(savedStaff);
            }
        }

        System.out.println("✅ Seeded admin user (admin/admin123) + 5 sample staff");
    }
}
