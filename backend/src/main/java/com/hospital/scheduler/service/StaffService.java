package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.StaffRequest;
import com.hospital.scheduler.dto.request.StaffSearchRequest;
import com.hospital.scheduler.dto.response.StaffResponse;
import com.hospital.scheduler.entity.AppRole;
import com.hospital.scheduler.entity.AuditHistory;
import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.entity.StaffRole;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.exception.ConflictException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.AppRoleRepository;
import com.hospital.scheduler.repository.SpecialtyRepository;
import com.hospital.scheduler.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.ss.usermodel.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class StaffService {

    private final StaffRepository staffRepository;
    private final SpecialtyRepository specialtyRepository;
    private final AppRoleRepository appRoleRepository;
    private final AuditHistoryService auditHistoryService;
    private final PasswordEncoder passwordEncoder;

    public List<StaffResponse> getAllStaff() {
        return staffRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<StaffResponse> getActiveStaff() {
        return staffRepository.findByIsActiveTrue().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public StaffResponse getStaffById(Integer id) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân sự với ID: " + id));
        return toResponse(staff);
    }

    public List<StaffResponse> searchStaffs(StaffSearchRequest request) {
        String keyword = (request.getKeyword() != null && !request.getKeyword().isBlank()) ? request.getKeyword() : null;
        String status = (request.getStatus() != null && !request.getStatus().isBlank()) ? request.getStatus().toUpperCase() : null;

        return staffRepository.searchStaffs(keyword, request.getSpecialtyId(), status).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public StaffResponse createStaff(StaffRequest request, List<String> roles) {
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BadRequestException("Password không được để trống");
        }
        if (staffRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Username '" + request.getUsername() + "' đã tồn tại");
        }
        if (request.getEmail() != null && staffRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email '" + request.getEmail() + "' đã tồn tại");
        }

        Specialty specialty = null;
        if (request.getSpecialtyId() != null) {
            specialty = specialtyRepository.findById(request.getSpecialtyId()).orElse(null);
        }

        String status = request.getStatus() != null ? request.getStatus() : "ACTIVE";
        boolean isActive = !"INACTIVE".equalsIgnoreCase(status);

        Staff staff = Staff.builder()
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .email(request.getEmail())
                .specialty(specialty)
                .maxShiftsPerMonth(request.getMaxShiftsPerMonth() != null ? request.getMaxShiftsPerMonth() : 5)
                .isActive(isActive)
                .status(status)
                .staffRoles(new HashSet<>())
                .build();

        Staff saved = staffRepository.save(staff);

        // Assign roles
        if (roles != null && !roles.isEmpty()) {
            for (String roleName : roles) {
                AppRole role = appRoleRepository.findByName(roleName)
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy role: " + roleName));
                StaffRole sr = StaffRole.builder()
                        .staffId(saved.getId())
                        .roleId(role.getId())
                        .build();
                saved.getStaffRoles().add(sr);
            }
            staffRepository.save(saved);
        }

        return toResponse(staffRepository.findByIdWithRoles(saved.getId()).orElse(saved));
    }

    public StaffResponse updateStaff(Integer id, StaffRequest request) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân sự với ID: " + id));

        staffRepository.findByUsername(request.getUsername())
                .filter(s -> !s.getId().equals(id))
                .ifPresent(s -> {
                    throw new ConflictException("Username '" + request.getUsername() + "' đã tồn tại");
                });

        if (request.getEmail() != null) {
            staffRepository.findByEmail(request.getEmail())
                    .filter(s -> !s.getId().equals(id))
                    .ifPresent(s -> {
                        throw new ConflictException("Email '" + request.getEmail() + "' đã tồn tại");
                    });
        }

        Staff oldStaff = Staff.builder()
                .username(staff.getUsername())
                .fullName(staff.getFullName())
                .phone(staff.getPhone())
                .email(staff.getEmail())
                .maxShiftsPerMonth(staff.getMaxShiftsPerMonth())
                .specialty(staff.getSpecialty())
                .isActive(staff.getIsActive())
                .status(staff.getStatus())
                .build();

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            staff.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        staff.setUsername(request.getUsername());
        staff.setFullName(request.getFullName());
        staff.setPhone(request.getPhone());
        staff.setEmail(request.getEmail());
        staff.setMaxShiftsPerMonth(request.getMaxShiftsPerMonth() != null ? request.getMaxShiftsPerMonth() : 5);

        if (request.getSpecialtyId() != null) {
            Specialty specialty = specialtyRepository.findById(request.getSpecialtyId()).orElse(null);
            staff.setSpecialty(specialty);
        }

        if (request.getStatus() != null) {
            staff.setStatus(request.getStatus());
            staff.setIsActive(!"INACTIVE".equalsIgnoreCase(request.getStatus()));
        }

        // Update roles
        if (request.getRoles() != null) {
            List<AppRole> targetRoles = new java.util.ArrayList<>();
            for (String roleName : request.getRoles()) {
                AppRole role = appRoleRepository.findByName(roleName)
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy role: " + roleName));
                targetRoles.add(role);
            }

            // Identify roles to remove
            List<StaffRole> toRemove = staff.getStaffRoles().stream()
                    .filter(sr -> targetRoles.stream().noneMatch(tr -> tr.getId().equals(sr.getRoleId())))
                    .collect(Collectors.toList());

            // Identify role IDs to add
            List<AppRole> toAdd = targetRoles.stream()
                    .filter(tr -> staff.getStaffRoles().stream().noneMatch(sr -> sr.getRoleId().equals(tr.getId())))
                    .collect(Collectors.toList());

            // Apply removals
            staff.getStaffRoles().removeAll(toRemove);

            // Apply additions
            for (AppRole role : toAdd) {
                StaffRole sr = StaffRole.builder()
                        .staffId(staff.getId())
                        .roleId(role.getId())
                        .staff(staff)
                        .role(role)
                        .build();
                staff.getStaffRoles().add(sr);
            }
        }

        Staff saved = staffRepository.save(staff);

        auditHistoryService.logAction("staff", id, AuditHistory.ActionType.UPDATE, oldStaff, saved, null);

        return toResponse(saved);
    }

    public void deleteStaff(Integer id) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân sự với ID: " + id));

        Staff oldStaff = Staff.builder()
                .username(staff.getUsername())
                .fullName(staff.getFullName())
                .isActive(staff.getIsActive())
                .status(staff.getStatus())
                .build();

        staff.setIsActive(false);
        staff.setStatus("INACTIVE");
        staff.setUpdatedAt(java.time.LocalDateTime.now());
        staffRepository.save(staff);

        auditHistoryService.logAction("staff", id, AuditHistory.ActionType.UPDATE, oldStaff, staff, null);
    }

    public StaffResponse getStaffByUsername(String username) {
        Staff staff = staffRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân sự: " + username));
        return toResponse(staff);
    }

    private StaffResponse toResponse(Staff staff) {
        StaffResponse.SpecialtyResponse specialtyResp = null;
        if (staff.getSpecialty() != null) {
            specialtyResp = StaffResponse.SpecialtyResponse.builder()
                    .id(staff.getSpecialty().getId())
                    .name(staff.getSpecialty().getName())
                    .build();
        }

        List<String> roleNames = staff.getStaffRoles().stream()
                .map(sr -> sr.getRole() != null ? sr.getRole().getName() : null)
                .filter(r -> r != null)
                .collect(Collectors.toList());

        return StaffResponse.builder()
                .id(staff.getId())
                .username(staff.getUsername())
                .fullName(staff.getFullName())
                .phone(staff.getPhone())
                .email(staff.getEmail())
                .specialty(specialtyResp)
                .maxShiftsPerMonth(staff.getMaxShiftsPerMonth())
                .isActive(staff.getIsActive())
                .status(staff.getStatus())
                .createdAt(staff.getCreatedAt())
                .updatedAt(staff.getUpdatedAt())
                .roles(roleNames)
                .build();
    }

    @Transactional
    public Map<String, Object> importStaffs(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("Tệp tải lên không được để trống");
        }
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new BadRequestException("Tên tệp không hợp lệ");
        }
        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        if (!extension.equals("xlsx") && !extension.equals("xls") && !extension.equals("csv")) {
            throw new BadRequestException("Định dạng tệp không được hỗ trợ. Chỉ nhận .xlsx, .xls, .csv");
        }

        List<StaffRequest> requests = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();

        // Load caches to optimize database accesses (O(1) lookups)
        Map<String, Specialty> specialtyMap = new HashMap<>();
        for (Specialty s : specialtyRepository.findAll()) {
            specialtyMap.put(s.getName().toLowerCase().trim(), s);
        }

        Map<String, AppRole> roleMap = new HashMap<>();
        for (AppRole r : appRoleRepository.findAll()) {
            roleMap.put(r.getName().toUpperCase().trim(), r);
        }

        Map<String, Staff> existingUsernames = new HashMap<>();
        for (Staff s : staffRepository.findAll()) {
            existingUsernames.put(s.getUsername().toLowerCase().trim(), s);
        }

        Map<String, Staff> existingEmails = new HashMap<>();
        for (Staff s : staffRepository.findAll()) {
            if (s.getEmail() != null && !s.getEmail().isBlank()) {
                existingEmails.put(s.getEmail().toLowerCase().trim(), s);
            }
        }

        if (extension.equals("csv")) {
            parseCsvFile(file, requests, errorMessages);
        } else {
            parseExcelFile(file, requests, errorMessages);
        }

        if (!errorMessages.isEmpty()) {
            throw new BadRequestException("Tệp tải lên chứa dữ liệu không hợp lệ. Vui lòng sửa các lỗi sau:\n" + String.join("\n", errorMessages));
        }

        // Validate duplicates and logic check
        Set<String> fileUsernames = new HashSet<>();
        Set<String> fileEmails = new HashSet<>();
        List<Staff> toSaveNew = new ArrayList<>();
        List<Staff> toSaveUpdate = new ArrayList<>();
        
        // Save current roles association helper to process later
        Map<Staff, List<AppRole>> staffTargetRolesMap = new HashMap<>();
        Map<Staff, Staff> auditOldNewMap = new HashMap<>();

        for (int i = 0; i < requests.size(); i++) {
            StaffRequest req = requests.get(i);
            int lineNum = i + 2; // Row index (1-based, plus header row)

            String username = cleanString(req.getUsername());
            String fullName = cleanString(req.getFullName());
            String email = cleanString(req.getEmail());
            String phone = cleanString(req.getPhone());
            String specName = cleanString(req.getSpecialtyName());
            String status = cleanString(req.getStatus());
            Integer maxShifts = req.getMaxShiftsPerMonth();
            List<String> reqRoles = req.getRoles();

            if (username.isEmpty()) {
                errorMessages.add("Dòng " + lineNum + " - Cột Username: Không được để trống");
                continue;
            }
            if (fullName.isEmpty()) {
                errorMessages.add("Dòng " + lineNum + " - Cột Họ tên: Không được để trống");
                continue;
            }

            // Check duplicates in file
            if (fileUsernames.contains(username.toLowerCase())) {
                errorMessages.add("Dòng " + lineNum + " - Cột Username: Username '" + username + "' bị trùng lặp trong tệp");
            } else {
                fileUsernames.add(username.toLowerCase());
            }

            if (!email.isEmpty()) {
                if (!email.matches("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$")) {
                    errorMessages.add("Dòng " + lineNum + " - Cột Email: Định dạng email không hợp lệ");
                }
                if (fileEmails.contains(email.toLowerCase())) {
                    errorMessages.add("Dòng " + lineNum + " - Cột Email: Email '" + email + "' bị trùng lặp trong tệp");
                } else {
                    fileEmails.add(email.toLowerCase());
                }
            }

            if (!phone.isEmpty() && !phone.matches("^[0-9]{10,11}$")) {
                errorMessages.add("Dòng " + lineNum + " - Cột Số điện thoại: Số điện thoại phải từ 10 đến 11 chữ số");
            }

            // Map Specialty
            Specialty specialty = null;
            if (!specName.isEmpty()) {
                specialty = specialtyMap.get(specName.toLowerCase());
                if (specialty == null) {
                    errorMessages.add("Dòng " + lineNum + " - Cột Chuyên khoa: Chuyên khoa '" + specName + "' không tồn tại");
                }
            }

            // Map Roles
            List<AppRole> targetRoles = new ArrayList<>();
            if (reqRoles != null) {
                for (String rn : reqRoles) {
                    String roleName = cleanString(rn).toUpperCase();
                    if (!roleName.isEmpty()) {
                        AppRole role = roleMap.get(roleName);
                        if (role == null) {
                            errorMessages.add("Dòng " + lineNum + " - Cột Vai trò: Vai trò '" + rn + "' không hợp lệ");
                        } else {
                            targetRoles.add(role);
                        }
                    }
                }
            }

            // Map Status
            String normalizedStatus = "ACTIVE";
            if (!status.isEmpty()) {
                if (status.equalsIgnoreCase("Đang làm việc") || status.equalsIgnoreCase("ACTIVE")) {
                    normalizedStatus = "ACTIVE";
                } else if (status.equalsIgnoreCase("Nghỉ phép") || status.equalsIgnoreCase("ON_LEAVE")) {
                    normalizedStatus = "ON_LEAVE";
                } else if (status.equalsIgnoreCase("Đã nghỉ") || status.equalsIgnoreCase("INACTIVE")) {
                    normalizedStatus = "INACTIVE";
                } else {
                    errorMessages.add("Dòng " + lineNum + " - Cột Trạng thái: Trạng thái '" + status + "' không hợp lệ");
                }
            }
            boolean isActive = !normalizedStatus.equals("INACTIVE");

            // Look up existing staff in DB
            Staff existing = null;
            if (req.getId() != null) {
                existing = staffRepository.findById(req.getId()).orElse(null);
                if (existing == null) {
                    errorMessages.add("Dòng " + lineNum + " - Cột ID: Không tìm thấy nhân viên có ID = " + req.getId());
                    continue;
                }
            } else {
                existing = existingUsernames.get(username.toLowerCase());
            }

            // Check conflict
            if (existing != null) {
                // If username is changing to something that belongs to a different record
                Staff usernameConf = existingUsernames.get(username.toLowerCase());
                if (usernameConf != null && !usernameConf.getId().equals(existing.getId())) {
                    errorMessages.add("Dòng " + lineNum + " - Cột Username: Username '" + username + "' đã được sử dụng bởi nhân viên khác");
                }
                
                // If email is changing to something that belongs to a different record
                if (!email.isEmpty()) {
                    Staff emailConf = existingEmails.get(email.toLowerCase());
                    if (emailConf != null && !emailConf.getId().equals(existing.getId())) {
                        errorMessages.add("Dòng " + lineNum + " - Cột Email: Email '" + email + "' đã được sử dụng bởi nhân viên khác");
                    }
                }
            } else {
                // For new creations
                if (existingUsernames.containsKey(username.toLowerCase())) {
                    errorMessages.add("Dòng " + lineNum + " - Cột Username: Username '" + username + "' đã tồn tại trong hệ thống");
                }
                if (!email.isEmpty() && existingEmails.containsKey(email.toLowerCase())) {
                    errorMessages.add("Dòng " + lineNum + " - Cột Email: Email '" + email + "' đã tồn tại trong hệ thống");
                }
            }

            if (!errorMessages.isEmpty()) {
                continue;
            }

            // Build or Update entities
            if (existing != null) {
                // Clone old staff for audit logging
                Staff oldStaff = Staff.builder()
                        .username(existing.getUsername())
                        .fullName(existing.getFullName())
                        .phone(existing.getPhone())
                        .email(existing.getEmail())
                        .maxShiftsPerMonth(existing.getMaxShiftsPerMonth())
                        .specialty(existing.getSpecialty())
                        .isActive(existing.getIsActive())
                        .status(existing.getStatus())
                        .build();

                existing.setUsername(username);
                existing.setFullName(fullName);
                existing.setPhone(phone.isEmpty() ? null : phone);
                existing.setEmail(email.isEmpty() ? null : email);
                existing.setSpecialty(specialty);
                existing.setMaxShiftsPerMonth(maxShifts != null ? maxShifts : 5);
                existing.setStatus(normalizedStatus);
                existing.setIsActive(isActive);

                toSaveUpdate.add(existing);
                staffTargetRolesMap.put(existing, targetRoles);
                auditOldNewMap.put(existing, oldStaff);
            } else {
                Staff newStaff = Staff.builder()
                        .username(username)
                        .passwordHash(passwordEncoder.encode(username)) // Default password is username
                        .fullName(fullName)
                        .phone(phone.isEmpty() ? null : phone)
                        .email(email.isEmpty() ? null : email)
                        .specialty(specialty)
                        .maxShiftsPerMonth(maxShifts != null ? maxShifts : 5)
                        .status(normalizedStatus)
                        .isActive(isActive)
                        .staffRoles(new HashSet<>())
                        .build();

                toSaveNew.add(newStaff);
                staffTargetRolesMap.put(newStaff, targetRoles);
            }
        }

        if (!errorMessages.isEmpty()) {
            throw new BadRequestException("Tệp tải lên chứa dữ liệu không hợp lệ. Vui lòng sửa các lỗi sau:\n" + String.join("\n", errorMessages));
        }

        // Process save/update in database
        // 1. Save new staff first to generate their IDs
        if (!toSaveNew.isEmpty()) {
            staffRepository.saveAll(toSaveNew);
        }

        // 2. Setup roles for new staff
        for (Staff ns : toSaveNew) {
            List<AppRole> targetRoles = staffTargetRolesMap.get(ns);
            if (targetRoles != null) {
                for (AppRole r : targetRoles) {
                    StaffRole sr = StaffRole.builder()
                            .staffId(ns.getId())
                            .roleId(r.getId())
                            .staff(ns)
                            .role(r)
                            .build();
                    ns.getStaffRoles().add(sr);
                }
            }
        }

        // 3. Update roles for existing staff using delta updates
        for (Staff us : toSaveUpdate) {
            List<AppRole> targetRoles = staffTargetRolesMap.get(us);
            if (targetRoles != null) {
                List<StaffRole> toRemove = us.getStaffRoles().stream()
                        .filter(sr -> targetRoles.stream().noneMatch(tr -> tr.getId().equals(sr.getRoleId())))
                        .collect(Collectors.toList());

                List<AppRole> toAdd = targetRoles.stream()
                        .filter(tr -> us.getStaffRoles().stream().noneMatch(sr -> sr.getRoleId().equals(tr.getId())))
                        .collect(Collectors.toList());

                us.getStaffRoles().removeAll(toRemove);

                for (AppRole r : toAdd) {
                    StaffRole sr = StaffRole.builder()
                            .staffId(us.getId())
                            .roleId(r.getId())
                            .staff(us)
                            .role(r)
                            .build();
                    us.getStaffRoles().add(sr);
                }
            }
        }

        // 4. Save updates and new staff roles
        List<Staff> allSaved = new ArrayList<>();
        allSaved.addAll(toSaveNew);
        allSaved.addAll(toSaveUpdate);
        
        if (!allSaved.isEmpty()) {
            staffRepository.saveAll(allSaved);
        }

        // 5. Write audit logs
        for (Staff us : toSaveUpdate) {
            Staff oldStaff = auditOldNewMap.get(us);
            auditHistoryService.logAction("staff", us.getId(), AuditHistory.ActionType.UPDATE, oldStaff, us, null);
        }
        for (Staff ns : toSaveNew) {
            auditHistoryService.logAction("staff", ns.getId(), AuditHistory.ActionType.INSERT, null, ns, null);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("successCount", allSaved.size());
        result.put("message", "Nhập thành công " + allSaved.size() + " nhân sự!");
        return result;
    }

    private void parseCsvFile(MultipartFile file, List<StaffRequest> requests, List<String> errorMessages) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNum = 0;
            while ((line = br.readLine()) != null) {
                lineNum++;
                if (lineNum == 1) {
                    continue; // Skip header
                }
                if (line.trim().isEmpty()) {
                    continue;
                }
                List<String> columns = parseCsvLine(line);
                while (columns.size() < 9) {
                    columns.add("");
                }

                StaffRequest req = new StaffRequest();
                String idStr = cleanString(columns.get(0));
                if (!idStr.isEmpty()) {
                    try {
                        req.setId(Integer.parseInt(idStr));
                    } catch (NumberFormatException e) {
                        errorMessages.add("Dòng " + lineNum + " - Cột ID: ID không đúng định dạng số");
                    }
                }
                req.setUsername(cleanString(columns.get(1)));
                req.setFullName(cleanString(columns.get(2)));
                req.setEmail(cleanString(columns.get(3)));
                req.setPhone(cleanString(columns.get(4)));
                req.setSpecialtyName(cleanString(columns.get(5)));
                
                String maxShiftsStr = cleanString(columns.get(6));
                if (!maxShiftsStr.isEmpty()) {
                    try {
                        req.setMaxShiftsPerMonth(Integer.parseInt(maxShiftsStr));
                    } catch (NumberFormatException e) {
                        errorMessages.add("Dòng " + lineNum + " - Cột Max ca/tháng: Phải là định dạng số");
                    }
                } else {
                    req.setMaxShiftsPerMonth(5);
                }

                String rolesStr = cleanString(columns.get(7));
                List<String> rolesList = new ArrayList<>();
                if (!rolesStr.isEmpty()) {
                    for (String r : rolesStr.split(",")) {
                        rolesList.add(r.trim());
                    }
                }
                req.setRoles(rolesList);
                req.setStatus(cleanString(columns.get(8)));

                requests.add(req);
            }
        } catch (Exception e) {
            throw new BadRequestException("Lỗi đọc tệp CSV: " + e.getMessage());
        }
    }

    private void parseExcelFile(MultipartFile file, List<StaffRequest> requests, List<String> errorMessages) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row)) {
                    continue;
                }
                int lineNum = i + 1;
                StaffRequest req = new StaffRequest();

                // 0: ID
                String idStr = cleanString(getCellValueAsString(row.getCell(0)));
                if (!idStr.isEmpty()) {
                    try {
                        if (idStr.contains(".")) {
                            idStr = idStr.substring(0, idStr.indexOf("."));
                        }
                        req.setId(Integer.parseInt(idStr));
                    } catch (NumberFormatException e) {
                        errorMessages.add("Dòng " + lineNum + " - Cột ID: ID không đúng định dạng số");
                    }
                }

                // 1: Username
                req.setUsername(cleanString(getCellValueAsString(row.getCell(1))));
                // 2: Họ tên
                req.setFullName(cleanString(getCellValueAsString(row.getCell(2))));
                // 3: Email
                req.setEmail(cleanString(getCellValueAsString(row.getCell(3))));
                // 4: Số điện thoại
                req.setPhone(cleanString(getCellValueAsString(row.getCell(4))));
                // 5: Chuyên khoa
                req.setSpecialtyName(cleanString(getCellValueAsString(row.getCell(5))));

                // 6: Max ca/tháng
                String maxShiftsStr = cleanString(getCellValueAsString(row.getCell(6)));
                if (!maxShiftsStr.isEmpty()) {
                    try {
                        if (maxShiftsStr.contains(".")) {
                            maxShiftsStr = maxShiftsStr.substring(0, maxShiftsStr.indexOf("."));
                        }
                        req.setMaxShiftsPerMonth(Integer.parseInt(maxShiftsStr));
                    } catch (NumberFormatException e) {
                        errorMessages.add("Dòng " + lineNum + " - Cột Max ca/tháng: Phải là định dạng số");
                    }
                } else {
                    req.setMaxShiftsPerMonth(5);
                }

                // 7: Vai trò
                String rolesStr = cleanString(getCellValueAsString(row.getCell(7)));
                List<String> rolesList = new ArrayList<>();
                if (!rolesStr.isEmpty()) {
                    for (String r : rolesStr.split(",")) {
                        rolesList.add(r.trim());
                    }
                }
                req.setRoles(rolesList);

                // 8: Trạng thái
                req.setStatus(cleanString(getCellValueAsString(row.getCell(8))));

                requests.add(req);
            }
        } catch (Exception e) {
            throw new BadRequestException("Lỗi đọc tệp Excel: " + e.getMessage());
        }
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        if (line.startsWith("\uFEFF")) {
            line = line.substring(1);
        }
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',') {
                if (inQuotes) {
                    sb.append(c);
                } else {
                    values.add(sb.toString());
                    sb.setLength(0);
                }
            } else {
                sb.append(c);
            }
        }
        values.add(sb.toString());
        return values;
    }

    private boolean isRowEmpty(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                double val = cell.getNumericCellValue();
                if (val == (long) val) {
                    return String.valueOf((long) val);
                }
                return String.valueOf(val);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        double numVal = cell.getNumericCellValue();
                        if (numVal == (long) numVal) {
                            return String.valueOf((long) numVal);
                        }
                        return String.valueOf(numVal);
                    } catch (Exception ex) {
                        return cell.getCellFormula();
                    }
                }
            default:
                return "";
        }
    }

    private String cleanString(String val) {
        if (val == null) return "";
        val = val.trim();
        if (val.startsWith("=\"") && val.endsWith("\"")) {
            val = val.substring(2, val.length() - 1);
        } else if (val.startsWith("=") && val.length() > 1) {
            val = val.substring(1);
        }
        if (val.startsWith("\"") && val.endsWith("\"")) {
            val = val.substring(1, val.length() - 1);
        }
        return val.trim();
    }
}
