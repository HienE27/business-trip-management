package com.hospital.scheduler.command;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.repository.*;
import com.hospital.scheduler.util.CompensationDateCalculator;
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
    private final ScheduleTemplateRepository scheduleTemplateRepository;
    private final CompensationDateCalculator compensationDateCalculator;
    private final HolidayRepository holidayRepository;

    // ⚠️  Muốn re-seed (thêm staff mới) → drop database + restart backend
    @Override
    public void run(String... args) {
        seedRoles();
        seedSpecialties();
        seedShiftTypes();
        seedHolidays();
        seedScheduleTemplates();
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

    private void seedHolidays() {
        if (holidayRepository.count() > 0) return;

        record HolidaySeed(String name, int month, int day, String description) {}
        HolidaySeed[] holidays = new HolidaySeed[]{
            new HolidaySeed("Tết Dương lịch",       1,  1,  "Năm mới Dương lịch"),
            new HolidaySeed("Tết Nguyên đán",        2,  14, "Tết Nguyên đán Ất Tỵ"),
            new HolidaySeed("Tết Nguyên đán",        2,  15, "Tết Nguyên đán Ất Tỵ"),
            new HolidaySeed("Tết Nguyên đán",        2,  16, "Tết Nguyên đán Ất Tỵ"),
            new HolidaySeed("Giỗ Tổ Hùng Vương",    4,  27, "Giỗ Tổ Hùng Vương"),
            new HolidaySeed("Ngày Giải phóng miền Nam", 4, 30, "Ngày Giải phóng miền Nam 30/4"),
            new HolidaySeed("Quốc tế Lao động",       5,  1,  "Ngày Quốc tế Lao động"),
            new HolidaySeed("Ngày Quốc khánh",         9,  2,  "Ngày Quốc khánh Việt Nam"),
        };

        for (HolidaySeed h : holidays) {
            LocalDate date = LocalDate.of(2026, h.month, h.day);
            if (!holidayRepository.findByDate(date).isPresent()) {
                holidayRepository.save(Holiday.builder()
                        .name(h.name).holidayDate(date).date(date).year(date.getYear()).description(h.description).isActive(true).build());
            }
        }
        System.out.println("✅ Seeded " + holidays.length + " holidays for 2026");
    }

    private void seedScheduleTemplates() {
        if (scheduleTemplateRepository.count() > 0) return;
        Specialty doctor = specialtyRepository.findByName("Bác sĩ").orElse(null);
        Specialty nurse = specialtyRepository.findByName("Điều dưỡng").orElse(null);

        record TemplateSeed(String name, String desc, int dayOfWeek, String shiftTypeId, Specialty specialty, int count) {}
        TemplateSeed[] templates = new TemplateSeed[]{
                new TemplateSeed("Trực 24/24 thứ 2", "Lịch trực 24/24 vào thứ 2", 1, "L01", doctor, 1),
                new TemplateSeed("Trực 24/24 thứ 3", "Lịch trực 24/24 vào thứ 3", 2, "L01", doctor, 1),
                new TemplateSeed("Trực 24/24 thứ 4", "Lịch trực 24/24 vào thứ 4", 3, "L01", doctor, 1),
                new TemplateSeed("Trực 24/24 thứ 5", "Lịch trực 24/24 vào thứ 5", 4, "L01", doctor, 1),
                new TemplateSeed("Trực 24/24 thứ 6", "Lịch trực 24/24 vào thứ 6", 5, "L01", doctor, 1),
                new TemplateSeed("Thông tầm thứ 2–6", "Ca thông tầm các ngày trong tuần", 1, "L02", doctor, 2),
                new TemplateSeed("PK dịch vụ thứ 2–6", "Phòng khám dịch vụ các ngày trong tuần", 1, "L03", nurse, 1),
                new TemplateSeed("PK chuyên gia thứ 7", "Phòng khám chuyên gia vào thứ 7", 6, "L04", doctor, 1),
        };

        for (TemplateSeed t : templates) {
            scheduleTemplateRepository.save(ScheduleTemplate.builder()
                    .name(t.name).description(t.desc)
                    .dayOfWeek(t.dayOfWeek).shiftTypeId(t.shiftTypeId)
                    .specialty(t.specialty).requiredStaffCount(t.count)
                    .isActive(true).build());
        }
        System.out.println("✅ Seeded " + templates.length + " schedule templates");
    }

    private void seedAdminUser() {
        if (staffRepository.count() > 0) return;

        AppRole adminRole = appRoleRepository.findByName("ADMIN").orElse(null);
        AppRole managerRole = appRoleRepository.findByName("MANAGER").orElse(null);
        AppRole staffRole = appRoleRepository.findByName("STAFF").orElse(null);
        Specialty doctorSpecialty = specialtyRepository.findByName("Bác sĩ").orElse(null);
        Specialty nurseSpecialty = specialtyRepository.findByName("Điều dưỡng").orElse(null);
        Specialty techSpecialty = specialtyRepository.findByName("Kỹ thuật viên").orElse(null);
        Specialty pharmaSpecialty = specialtyRepository.findByName("Dược sĩ").orElse(null);

        // ── ADMIN (ADMIN + MANAGER) ─────────────────────────────────────────
        Staff admin = staffRepository.save(Staff.builder()
                .username("admin").passwordHash(passwordEncoder.encode("admin123"))
                .fullName("Nguyễn Văn An").phone("0901000001")
                .email("admin@hospital.com").specialty(doctorSpecialty)
                .maxShiftsPerMonth(5).isActive(true).staffRoles(new HashSet<>()).build());
        addRoles(admin, adminRole, managerRole);

        // ── MANAGER (2 người) ──────────────────────────────────────────────
        Staff mgr1 = staffRepository.save(Staff.builder()
                .username("manager1").passwordHash(passwordEncoder.encode("123456"))
                .fullName("Trần Thị Bình").phone("0901000002")
                .email("manager1@hospital.com").specialty(doctorSpecialty)
                .maxShiftsPerMonth(4).isActive(true).staffRoles(new HashSet<>()).build());
        addRoles(mgr1, managerRole);

        Staff mgr2 = staffRepository.save(Staff.builder()
                .username("manager2").passwordHash(passwordEncoder.encode("123456"))
                .fullName("Lê Hoàng Cường").phone("0901000003")
                .email("manager2@hospital.com").specialty(nurseSpecialty)
                .maxShiftsPerMonth(4).isActive(true).staffRoles(new HashSet<>()).build());
        addRoles(mgr2, managerRole);

        // ── STAFF (17 người) ────────────────────────────────────────────────
        record StaffSeed(String username, String password, String fullName, String phone,
                        String email, Specialty specialty, int maxShifts) {}

        StaffSeed[] seeds = new StaffSeed[]{
                new StaffSeed("nvminh",    "123456", "Nguyễn Văn Minh",    "0901000004", "nvminh@hospital.com",    doctorSpecialty,  5),
                new StaffSeed("tthuhien",  "123456", "Trần Thu Hiền",     "0901000005", "tthuhien@hospital.com",  nurseSpecialty,   5),
                new StaffSeed("lbthanhtam","123456", "Lê Bùi Thanh Tâm",   "0901000006", "lbthanhtam@hospital.com",doctorSpecialty,  6),
                new StaffSeed("hpdat",     "123456", "Hoàng Phú Đạt",      "0901000007", "hpdat@hospital.com",     techSpecialty,    5),
                new StaffSeed("ntphuong",  "123456", "Ngô Thị Phượng",     "0901000008", "ntphuong@hospital.com",  pharmaSpecialty,  4),
                new StaffSeed("cmtuan",    "123456", "Chu Minh Tuấn",      "0901000009", "cmtuan@hospital.com",    doctorSpecialty,  5),
                new StaffSeed("dvanh",     "123456", "Đỗ Văn Anh",         "0901000010", "dvanh@hospital.com",     nurseSpecialty,   5),
                new StaffSeed("nthuylinh", "123456", "Nguyễn Thị Huyền Linh","0901000011","nthuylinh@hospital.com", doctorSpecialty,  6),
                new StaffSeed("vtquan",    "123456", "Vũ Trọng Quân",      "0901000012", "vtquan@hospital.com",    techSpecialty,    5),
                new StaffSeed("btdthu",    "123456", "Bùi Thị Diễm Thu",   "0901000013", "btdthu@hospital.com",    pharmaSpecialty,  4),
                new StaffSeed("nhduy",     "123456", "Nguyễn Hữu Duy",     "0901000014", "nhduy@hospital.com",     doctorSpecialty,  5),
                new StaffSeed("lthanhha",  "123456", "Lý Thị Thanh Hà",    "0901000015", "lthanhha@hospital.com",  nurseSpecialty,   5),
                new StaffSeed("dtqhieu",   "123456", "Đinh Trần Quang Hiếu","0901000016","dtqhieu@hospital.com",   doctorSpecialty,  6),
                new StaffSeed("pthanh",    "123456", "Phạm Thị Thanh",      "0901000017", "pthanh@hospital.com",     pharmaSpecialty,  4),
                new StaffSeed("vhhuy",     "123456", "Võ Hoàng Huy",        "0901000018", "vhhuy@hospital.com",      techSpecialty,    5),
                new StaffSeed("atducd",    "123456", "Anh Trần Đức",        "0901000019", "atducd@hospital.com",     doctorSpecialty,  5),
                new StaffSeed("dttthuy",   "123456", "Đặng Trần Thanh Thúy","0901000020","dttthuy@hospital.com",    nurseSpecialty,   5),
        };

        for (StaffSeed s : seeds) {
            Staff staff = staffRepository.save(Staff.builder()
                    .username(s.username).passwordHash(passwordEncoder.encode(s.password))
                    .fullName(s.fullName).phone(s.phone)
                    .email(s.email).specialty(s.specialty)
                    .maxShiftsPerMonth(s.maxShifts)
                    .isActive(true).staffRoles(new HashSet<>()).build());
            addRoles(staff, staffRole);
        }

        System.out.println("✅ Seeded: 1 admin + 2 manager + 17 staff = 20 total users");
    }

    private void addRoles(Staff staff, AppRole... roles) {
        for (AppRole role : roles) {
            if (role != null) {
                StaffRole sr = StaffRole.builder()
                        .staffId(staff.getId()).roleId(role.getId()).build();
                staff.getStaffRoles().add(sr);
            }
        }
        staffRepository.save(staff);
    }

    private void seedPeriodsAndSchedules() {
        Staff admin = staffRepository.findByUsername("admin").orElse(null);
        ShiftType l01 = shiftTypeRepository.findById("L01").orElse(null);
        ShiftType l02 = shiftTypeRepository.findById("L02").orElse(null);
        ShiftType l03 = shiftTypeRepository.findById("L03").orElse(null);
        ShiftType l04 = shiftTypeRepository.findById("L04").orElse(null);
        
        Specialty doctor = specialtyRepository.findByName("Bác sĩ").orElse(null);
        Specialty nurse = specialtyRepository.findByName("Điều dưỡng").orElse(null);

        // 1. Create period June 2026
        if (periodRepository.findByStartDateAndEndDate(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)).isEmpty()) {
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

            // Create shift requirements (sample)
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

            // Create sample schedules
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

                // Day 2: s2 has L01, s1 has L02 (conflict)
                Schedule sch2 = scheduleRepository.save(Schedule.builder()
                        .period(savedPeriod).workDate(LocalDate.of(2026, 6, 2)).staff(s2).shiftType(l01).hasConflict(false).build());
                createCompensationDayForSeed(sch2);

                scheduleRepository.save(Schedule.builder()
                        .period(savedPeriod).workDate(LocalDate.of(2026, 6, 2)).staff(s1).shiftType(l02).hasConflict(true).build());

                // Day 3: s3 has L01
                Schedule sch3 = scheduleRepository.save(Schedule.builder()
                        .period(savedPeriod).workDate(LocalDate.of(2026, 6, 3)).staff(s3).shiftType(l01).hasConflict(false).build());
                createCompensationDayForSeed(sch3);

                // Day 4: s2 has L02 on June 4th
                scheduleRepository.save(Schedule.builder()
                        .period(savedPeriod).workDate(LocalDate.of(2026, 6, 4)).staff(s2).shiftType(l02).hasConflict(false).build());

                // Day 5: s2 has L01 and s2 has L02 (conflict)
                scheduleRepository.save(Schedule.builder()
                        .period(savedPeriod).workDate(LocalDate.of(2026, 6, 5)).staff(s2).shiftType(l01).hasConflict(true).build());
                scheduleRepository.save(Schedule.builder()
                        .period(savedPeriod).workDate(LocalDate.of(2026, 6, 5)).staff(s2).shiftType(l02).hasConflict(true).build());
            }
            System.out.println("✅ Seeded sample published period June 2026");
        }

        // 2. Create period July 2026 (DRAFT status for M07 auto scheduling tests)
        if (periodRepository.findByStartDateAndEndDate(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)).isEmpty()) {
            SchedulePeriod draftPeriod = SchedulePeriod.builder()
                    .periodName("Kỳ tháng 07/2026")
                    .startDate(LocalDate.of(2026, 7, 1))
                    .endDate(LocalDate.of(2026, 7, 31))
                    .status(SchedulePeriod.PeriodStatus.DRAFT)
                    .generatedBy(admin)
                    .generatedAt(java.time.LocalDateTime.now())
                    .build();
            SchedulePeriod savedDraftPeriod = periodRepository.save(draftPeriod);

            // Create shift requirements for Draft period (so we have requirements to solve)
            for (int day = 1; day <= 5; day++) {
                LocalDate date = LocalDate.of(2026, 7, day);
                shiftRequirementRepository.save(ShiftRequirement.builder()
                        .period(savedDraftPeriod).workDate(date).shiftType(l01).specialty(doctor).requiredStaffCount(1).build());
                shiftRequirementRepository.save(ShiftRequirement.builder()
                        .period(savedDraftPeriod).workDate(date).shiftType(l02).specialty(doctor).requiredStaffCount(2).build());
                shiftRequirementRepository.save(ShiftRequirement.builder()
                        .period(savedDraftPeriod).workDate(date).shiftType(l03).specialty(nurse).requiredStaffCount(1).build());
                shiftRequirementRepository.save(ShiftRequirement.builder()
                        .period(savedDraftPeriod).workDate(date).shiftType(l04).specialty(doctor).requiredStaffCount(1).build());
            }
            System.out.println("✅ Seeded sample draft period July 2026 with requirements");
        }
    }

    private void createCompensationDayForSeed(Schedule schedule) {
        LocalDate shiftDate = schedule.getWorkDate();
        LocalDate compensationDate = compensationDateCalculator.calculateWithoutHolidays(shiftDate);

        // Check if compensation day already exists to avoid duplicates
        if (compensationDayRepository.findByStaffIdAndCompensationDate(
                schedule.getStaff().getId(), compensationDate).isPresent()) {
            return;
        }

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
}
