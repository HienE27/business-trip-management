package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.NotificationDTO;
import com.hospital.scheduler.dto.request.StaffRequest;
import com.hospital.scheduler.dto.request.StaffSearchRequest;
import com.hospital.scheduler.dto.response.StaffResponse;
import com.hospital.scheduler.entity.AppRole;
import com.hospital.scheduler.entity.AuditHistory;
import com.hospital.scheduler.entity.RoleName;
import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.entity.StaffRole;
import com.hospital.scheduler.entity.StaffStatus;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.exception.ConflictException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.AppRoleRepository;
import com.hospital.scheduler.repository.SpecialtyRepository;
import com.hospital.scheduler.repository.StaffRepository;
import com.hospital.scheduler.security.AuthContextService;
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
    private final AuthContextService authContextService;
    private final PasswordEncoder passwordEncoder;
    private final StaffImportParser staffImportParser;
    private final NotificationService notificationService;

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
        String role = (request.getRole() != null && !request.getRole().isBlank()) ? request.getRole().toUpperCase() : null;
        String position = (request.getPosition() != null && !request.getPosition().isBlank()) ? request.getPosition() : null;

        return staffRepository.searchStaffs(keyword, request.getSpecialtyId(), status, role, position).stream()
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

        String statusStr = request.getStatus() != null ? request.getStatus() : "ACTIVE";
        boolean isActive = !"INACTIVE".equalsIgnoreCase(statusStr);
        StaffStatus status = switch (statusStr.toUpperCase()) {
            case "ON_LEAVE" -> StaffStatus.ON_LEAVE;
            case "INACTIVE" -> StaffStatus.INACTIVE;
            default -> StaffStatus.ACTIVE;
        };

        Staff staff = Staff.builder()
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .email(request.getEmail())
                .position(request.getPosition())
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
                AppRole role = appRoleRepository.findByName(RoleName.valueOf(roleName.toUpperCase()))
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy role: " + roleName));
                StaffRole sr = StaffRole.builder()
                        .staffId(saved.getId())
                        .roleId(role.getId())
                        .build();
                saved.getStaffRoles().add(sr);
            }
            staffRepository.save(saved);
        }

        StaffResponse created = toResponse(staffRepository.findByIdWithRoles(saved.getId()).orElse(saved));
        auditHistoryService.logAction("staff", saved.getId(), AuditHistory.ActionType.INSERT, null, created, authContextService.getCurrentStaff().getId());
        return created;
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
                .position(staff.getPosition())
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
        staff.setPosition(request.getPosition());
        staff.setMaxShiftsPerMonth(request.getMaxShiftsPerMonth() != null ? request.getMaxShiftsPerMonth() : 5);

        if (request.getSpecialtyId() != null) {
            Specialty specialty = specialtyRepository.findById(request.getSpecialtyId()).orElse(null);
            staff.setSpecialty(specialty);
        }

        if (request.getStatus() != null) {
            StaffStatus newStatus = switch (request.getStatus().toUpperCase()) {
                case "ON_LEAVE" -> StaffStatus.ON_LEAVE;
                case "INACTIVE" -> StaffStatus.INACTIVE;
                default -> StaffStatus.ACTIVE;
            };
            staff.setStatus(newStatus);
            staff.setIsActive(!"INACTIVE".equalsIgnoreCase(request.getStatus()));
        }

        // Update roles
        if (request.getRoles() != null) {
            List<AppRole> targetRoles = new java.util.ArrayList<>();
            for (String roleName : request.getRoles()) {
                AppRole role = appRoleRepository.findByName(RoleName.valueOf(roleName.toUpperCase()))
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

        auditHistoryService.logAction("staff", id, AuditHistory.ActionType.UPDATE, oldStaff, saved, authContextService.getCurrentStaff().getId());

        // Notify staff that their profile was updated
        notificationService.createNotification(staff.getId(),
                new NotificationDTO("Cập nhật hồ sơ",
                        "Hồ sơ của bạn đã được cập nhật bởi quản lý."));

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
        staff.setStatus(StaffStatus.INACTIVE);
        staff.setUpdatedAt(java.time.LocalDateTime.now());
        staffRepository.save(staff);

        auditHistoryService.logAction("staff", id, AuditHistory.ActionType.UPDATE, oldStaff, staff, authContextService.getCurrentStaff().getId());
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
                .map(sr -> sr.getRole() != null ? sr.getRole().getName().name() : null)
                .filter(r -> r != null)
                .collect(Collectors.toList());

        return StaffResponse.builder()
                .id(staff.getId())
                .username(staff.getUsername())
                .fullName(staff.getFullName())
                .phone(staff.getPhone())
                .email(staff.getEmail())
                .position(staff.getPosition())
                .specialty(specialtyResp)
                .maxShiftsPerMonth(staff.getMaxShiftsPerMonth())
                .isActive(staff.getIsActive())
                .status(staff.getStatus().name())
                .createdAt(staff.getCreatedAt())
                .updatedAt(staff.getUpdatedAt())
                .roles(roleNames)
                .build();
    }

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    @Transactional
    public Map<String, Object> importStaffs(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("Tệp tải lên không được để trống");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("Kích thước tệp không được vượt quá 5MB");
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
            roleMap.put(r.getName().name().toUpperCase().trim(), r);
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
            staffImportParser.parseFile(file, extension, requests, errorMessages);
        } else {
            staffImportParser.parseFile(file, extension, requests, errorMessages);
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
            StaffStatus normalizedStatus = StaffStatus.ACTIVE;
            if (!status.isEmpty()) {
                if (status.equalsIgnoreCase("Đang làm việc") || status.equalsIgnoreCase("ACTIVE")) {
                    normalizedStatus = StaffStatus.ACTIVE;
                } else if (status.equalsIgnoreCase("Nghỉ phép") || status.equalsIgnoreCase("ON_LEAVE") || status.equalsIgnoreCase("Đã nghỉ") || status.equalsIgnoreCase("INACTIVE")) {
                    normalizedStatus = StaffStatus.INACTIVE;
                } else {
                    errorMessages.add("Dòng " + lineNum + " - Cột Trạng thái: Trạng thái '" + status + "' không hợp lệ");
                }
            }
            boolean isActive = normalizedStatus == StaffStatus.ACTIVE;

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
        Integer currentStaffId = authContextService.getCurrentStaff().getId();
        for (Staff us : toSaveUpdate) {
            Staff oldStaff = auditOldNewMap.get(us);
            auditHistoryService.logAction("staff", us.getId(), AuditHistory.ActionType.UPDATE, oldStaff, us, currentStaffId);
        }
        for (Staff ns : toSaveNew) {
            auditHistoryService.logAction("staff", ns.getId(), AuditHistory.ActionType.INSERT, null, ns, currentStaffId);
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
            Map<String, Integer> colMap = new HashMap<>();
            while ((line = br.readLine()) != null) {
                lineNum++;
                if (lineNum == 1) {
                    List<String> headers = parseCsvLine(line);
                    for (int i = 0; i < headers.size(); i++) {
                        String h = headers.get(i).trim().toLowerCase();
                        if (h.startsWith("\uFEFF")) {
                            h = h.substring(1);
                        }
                        colMap.put(h, i);
                    }
                    continue; // Skip header
                }
                if (line.trim().isEmpty()) {
                    continue;
                }
                List<String> columns = parseCsvLine(line);

                StaffRequest req = new StaffRequest();
                Integer idIdx = colMap.get("id");
                Integer usernameIdx = colMap.get("username");
                Integer fullNameIdx = colMap.get("họ tên");
                Integer emailIdx = colMap.get("email");
                Integer phoneIdx = colMap.get("số điện thoại");
                Integer specialtyIdx = colMap.get("chuyên khoa");
                Integer maxShiftsIdx = colMap.get("max ca/tháng");
                Integer rolesIdx = colMap.get("vai trò");
                Integer statusIdx = colMap.get("trạng thái");

                if (idIdx != null && idIdx < columns.size()) {
                    String idStr = cleanString(columns.get(idIdx));
                    if (!idStr.isEmpty()) {
                        try {
                            req.setId(Integer.parseInt(idStr));
                        } catch (NumberFormatException e) {
                            errorMessages.add("Dòng " + lineNum + " - Cột ID: ID không đúng định dạng số");
                        }
                    }
                }
                if (usernameIdx != null && usernameIdx < columns.size()) {
                    req.setUsername(cleanString(columns.get(usernameIdx)));
                } else {
                    req.setUsername("");
                }
                if (fullNameIdx != null && fullNameIdx < columns.size()) {
                    req.setFullName(cleanString(columns.get(fullNameIdx)));
                } else {
                    req.setFullName("");
                }
                if (emailIdx != null && emailIdx < columns.size()) {
                    req.setEmail(cleanString(columns.get(emailIdx)));
                } else {
                    req.setEmail("");
                }
                if (phoneIdx != null && phoneIdx < columns.size()) {
                    req.setPhone(cleanString(columns.get(phoneIdx)));
                } else {
                    req.setPhone("");
                }
                if (specialtyIdx != null && specialtyIdx < columns.size()) {
                    req.setSpecialtyName(cleanString(columns.get(specialtyIdx)));
                } else {
                    req.setSpecialtyName("");
                }
                
                if (maxShiftsIdx != null && maxShiftsIdx < columns.size()) {
                    String maxShiftsStr = cleanString(columns.get(maxShiftsIdx));
                    if (!maxShiftsStr.isEmpty()) {
                        try {
                            req.setMaxShiftsPerMonth(Integer.parseInt(maxShiftsStr));
                        } catch (NumberFormatException e) {
                            errorMessages.add("Dòng " + lineNum + " - Cột Max ca/tháng: Phải là định dạng số");
                        }
                    } else {
                        req.setMaxShiftsPerMonth(5);
                    }
                } else {
                    req.setMaxShiftsPerMonth(5);
                }

                if (rolesIdx != null && rolesIdx < columns.size()) {
                    String rolesStr = cleanString(columns.get(rolesIdx));
                    List<String> rolesList = new ArrayList<>();
                    if (!rolesStr.isEmpty()) {
                        for (String r : rolesStr.split(",")) {
                            rolesList.add(r.trim());
                        }
                    }
                    req.setRoles(rolesList);
                } else {
                    req.setRoles(new ArrayList<>());
                }

                if (statusIdx != null && statusIdx < columns.size()) {
                    req.setStatus(cleanString(columns.get(statusIdx)));
                } else {
                    req.setStatus("");
                }

                requests.add(req);
            }
        } catch (Exception e) {
            throw new BadRequestException("Lỗi đọc tệp CSV: " + e.getMessage());
        }
    }

    private void parseExcelFile(MultipartFile file, List<StaffRequest> requests, List<String> errorMessages) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BadRequestException("Tệp Excel không có dòng tiêu đề");
            }
            Map<String, Integer> colMap = new HashMap<>();
            for (int cellNum = 0; cellNum < headerRow.getLastCellNum(); cellNum++) {
                Cell cell = headerRow.getCell(cellNum);
                if (cell != null) {
                    String h = getCellValueAsString(cell).trim().toLowerCase();
                    colMap.put(h, cellNum);
                }
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row)) {
                    continue;
                }
                int lineNum = i + 1;
                StaffRequest req = new StaffRequest();

                Integer idIdx = colMap.get("id");
                Integer usernameIdx = colMap.get("username");
                Integer fullNameIdx = colMap.get("họ tên");
                Integer emailIdx = colMap.get("email");
                Integer phoneIdx = colMap.get("số điện thoại");
                Integer specialtyIdx = colMap.get("chuyên khoa");
                Integer maxShiftsIdx = colMap.get("max ca/tháng");
                Integer rolesIdx = colMap.get("vai trò");
                Integer statusIdx = colMap.get("trạng thái");

                if (idIdx != null) {
                    String idStr = cleanString(getCellValueAsString(row.getCell(idIdx)));
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
                }

                if (usernameIdx != null) {
                    req.setUsername(cleanString(getCellValueAsString(row.getCell(usernameIdx))));
                } else {
                    req.setUsername("");
                }
                if (fullNameIdx != null) {
                    req.setFullName(cleanString(getCellValueAsString(row.getCell(fullNameIdx))));
                } else {
                    req.setFullName("");
                }
                if (emailIdx != null) {
                    req.setEmail(cleanString(getCellValueAsString(row.getCell(emailIdx))));
                } else {
                    req.setEmail("");
                }
                if (phoneIdx != null) {
                    req.setPhone(cleanString(getCellValueAsString(row.getCell(phoneIdx))));
                } else {
                    req.setPhone("");
                }
                if (specialtyIdx != null) {
                    req.setSpecialtyName(cleanString(getCellValueAsString(row.getCell(specialtyIdx))));
                } else {
                    req.setSpecialtyName("");
                }

                if (maxShiftsIdx != null) {
                    String maxShiftsStr = cleanString(getCellValueAsString(row.getCell(maxShiftsIdx)));
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
                } else {
                    req.setMaxShiftsPerMonth(5);
                }

                if (rolesIdx != null) {
                    String rolesStr = cleanString(getCellValueAsString(row.getCell(rolesIdx)));
                    List<String> rolesList = new ArrayList<>();
                    if (!rolesStr.isEmpty()) {
                        for (String r : rolesStr.split(",")) {
                            rolesList.add(r.trim());
                        }
                    }
                    req.setRoles(rolesList);
                } else {
                    req.setRoles(new ArrayList<>());
                }

                if (statusIdx != null) {
                    req.setStatus(cleanString(getCellValueAsString(row.getCell(statusIdx))));
                } else {
                    req.setStatus("");
                }

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
