package com.hospital.scheduler.command;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;

@Component
@RequiredArgsConstructor
@Transactional
public class DataSeeder implements CommandLineRunner {

    private final AppRoleRepository appRoleRepository;
    private final StaffRepository staffRepository;
    private final SpecialtyRepository specialtyRepository;
    private final ShiftTypeRepository shiftTypeRepository;
    private final PasswordEncoder passwordEncoder;
    private final SchedulePeriodRepository periodRepository;
    private final ScheduleRepository scheduleRepository;
    private final ShiftRequirementRepository shiftRequirementRepository;
    private final CompensationDayRepository compensationDayRepository;

    @Override
    public void run(String... args) {
        seedRoles();
        seedSpecialties();
        seedShiftTypes();
        seedAdminUser();
        seedPeriodsAndSchedules();
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

    private void seedPeriodsAndSchedules() {
        if (periodRepository.count() > 0) return;

        Staff admin = staffRepository.findByUsername("admin").orElse(null);
        ShiftType l01 = shiftTypeRepository.findById("L01").orElse(null);
        ShiftType l02 = shiftTypeRepository.findById("L02").orElse(null);
        ShiftType l03 = shiftTypeRepository.findById("L03").orElse(null);
        ShiftType l04 = shiftTypeRepository.findById("L04").orElse(null);
        
        Specialty doctor = specialtyRepository.findByName("Bác sĩ").orElse(null);
        Specialty nurse = specialtyRepository.findByName("Điều dưỡng").orElse(null);

        // 1. Create period
        SchedulePeriod period = SchedulePeriod.builder()
                .periodName("Kỳ tháng 06/2026")
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .status(SchedulePeriod.PeriodStatus.PUBLISHED)
                .generatedBy(admin)
                .generatedAt(java.time.LocalDateTime.now())
                .publishedAt(java.time.LocalDateTime.now())
                .build();
        SchedulePeriod savedPeriod = periodRepository.save(period);

        // 2. Create shift requirements (sample)
        // Let's seed requirements for the first 5 days of June 2026
        for (int day = 1; day <= 5; day++) {
            LocalDate date = LocalDate.of(2026, 6, day);
            shiftRequirementRepository.save(ShiftRequirement.builder()
                    .period(savedPeriod).workDate(date).shiftType(l01).specialty(doctor).requiredStaffCount(1).build());
            shiftRequirementRepository.save(ShiftRequirement.builder()
                    .period(savedPeriod).workDate(date).shiftType(l02).specialty(doctor).requiredStaffCount(2).build());
            shiftRequirementRepository.save(ShiftRequirement.builder()
                    .period(savedPeriod).workDate(date).shiftType(l03).specialty(nurse).requiredStaffCount(1).build());
            shiftRequirementRepository.save(ShiftRequirement.builder()
                    .period(savedPeriod).workDate(date).shiftType(l04).specialty(doctor).requiredStaffCount(1).build());
        }

        // 3. Create sample schedules
        List<Staff> staffList = staffRepository.findByIsActiveTrue();
        if (staffList.size() >= 5) {
            Staff s1 = staffList.get(1); // staff1
            Staff s2 = staffList.get(2); // staff2
            Staff s3 = staffList.get(3); // staff3
            Staff s4 = staffList.get(4); // staff4
            Staff s5 = staffList.get(0); // admin

            // Day 1: s1 has L01, s4 has L02, s5 has L03
            Schedule sch1 = scheduleRepository.save(Schedule.builder()
                    .period(savedPeriod).workDate(LocalDate.of(2026, 6, 1)).staff(s1).shiftType(l01).hasConflict(false).build());
            createCompensationDayForSeed(sch1);

            scheduleRepository.save(Schedule.builder()
                    .period(savedPeriod).workDate(LocalDate.of(2026, 6, 1)).staff(s4).shiftType(l02).hasConflict(false).build());
            scheduleRepository.save(Schedule.builder()
                    .period(savedPeriod).workDate(LocalDate.of(2026, 6, 1)).staff(s5).shiftType(l03).hasConflict(false).build());

            // Day 2: s2 has L01, s1 has L02 (conflict because Day 2 is s1's compensation day!)
            Schedule sch2 = scheduleRepository.save(Schedule.builder()
                    .period(savedPeriod).workDate(LocalDate.of(2026, 6, 2)).staff(s2).shiftType(l01).hasConflict(false).build());
            createCompensationDayForSeed(sch2);

            // s1 has L02 on June 2nd, which is their compensation day (L01 on June 1st -> compensation on June 2nd)
            scheduleRepository.save(Schedule.builder()
                    .period(savedPeriod).workDate(LocalDate.of(2026, 6, 2)).staff(s1).shiftType(l02).hasConflict(true).build());

            // Day 3: s3 has L01
            Schedule sch3 = scheduleRepository.save(Schedule.builder()
                    .period(savedPeriod).workDate(LocalDate.of(2026, 6, 3)).staff(s3).shiftType(l01).hasConflict(false).build());
            createCompensationDayForSeed(sch3);

            // Day 4: s2 has L02 on June 4th (normal)
            scheduleRepository.save(Schedule.builder()
                    .period(savedPeriod).workDate(LocalDate.of(2026, 6, 4)).staff(s2).shiftType(l02).hasConflict(false).build());

            // Day 5: s2 has L01 and s2 has L02 (conflict: double shift same day!)
            scheduleRepository.save(Schedule.builder()
                    .period(savedPeriod).workDate(LocalDate.of(2026, 6, 5)).staff(s2).shiftType(l01).hasConflict(true).build());
            scheduleRepository.save(Schedule.builder()
                    .period(savedPeriod).workDate(LocalDate.of(2026, 6, 5)).staff(s2).shiftType(l02).hasConflict(true).build());
        }

        System.out.println("✅ Seeded sample period, requirements, and schedules with conflicts for testing");
    }

    private void createCompensationDayForSeed(Schedule schedule) {
        LocalDate shiftDate = schedule.getWorkDate();
        LocalDate compensationDate = calculateCompensationDateOnSeed(shiftDate);

        CompensationDay compDay = CompensationDay.builder()
                .schedule(schedule)
                .staff(schedule.getStaff())
                .period(schedule.getPeriod())
                .shiftDate(shiftDate)
                .compensationDate(compensationDate)
                .note("Ngày nghỉ bù tự động từ ca L01 (Seed)")
                .build();
        compensationDayRepository.save(compDay);
    }

    private LocalDate calculateCompensationDateOnSeed(LocalDate shiftDate) {
        java.time.DayOfWeek dow = shiftDate.getDayOfWeek();
        return switch (dow) {
            case MONDAY -> shiftDate.plusDays(1);
            case TUESDAY -> shiftDate.plusDays(1);
            case WEDNESDAY -> shiftDate.plusDays(1);
            case THURSDAY -> shiftDate.plusDays(1);
            case FRIDAY -> shiftDate.plusDays(4);
            case SATURDAY -> shiftDate.plusDays(3);
            case SUNDAY -> shiftDate.plusDays(1);
        };
    }
}
