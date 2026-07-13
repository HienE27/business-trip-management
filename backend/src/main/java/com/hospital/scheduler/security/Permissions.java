package com.hospital.scheduler.security;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Central catalog of every permission string used by the backend.
 *
 * <p>Use these constants in {@code @PreAuthorize("hasAuthority('...')")} so that
 * the controller layer cannot drift away from the seed data. The frontend
 * mirrors this list in {@code frontend/src/lib/permissions.ts}.
 *
 * <p>Naming convention: {@code <MODULE>_<ACTION[_SCOPE]>}. New permissions
 * MUST be appended here, seeded in {@code DataSeeder}, and mirrored on the
 * frontend before being used in any controller.
 */
public final class Permissions {

    // ── Dashboard ───────────────────────────────────────────────────────
    public static final String DASHBOARD_VIEW = "DASHBOARD_VIEW";
    public static final String DASHBOARD_AGGREGATE = "DASHBOARD_AGGREGATE";

    // ── Staff ───────────────────────────────────────────────────────────
    public static final String STAFF_VIEW = "STAFF_VIEW";
    public static final String STAFF_CREATE = "STAFF_CREATE";
    public static final String STAFF_UPDATE = "STAFF_UPDATE";
    public static final String STAFF_DELETE = "STAFF_DELETE";
    public static final String STAFF_IMPORT = "STAFF_IMPORT";

    // ── Role / Permission matrix ────────────────────────────────────────
    public static final String ROLE_VIEW = "ROLE_VIEW";
    public static final String ROLE_EDIT = "ROLE_EDIT";

    // ── Schedule period ─────────────────────────────────────────────────
    public static final String PERIOD_VIEW = "PERIOD_VIEW";
    public static final String PERIOD_CREATE = "PERIOD_CREATE";
    public static final String PERIOD_UPDATE = "PERIOD_UPDATE";
    public static final String PERIOD_DELETE = "PERIOD_DELETE";
    public static final String PERIOD_PUBLISH = "PERIOD_PUBLISH";
    public static final String PERIOD_ARCHIVE = "PERIOD_ARCHIVE";

    // ── Schedule (L01–L04) ──────────────────────────────────────────────
    public static final String SCHEDULE_VIEW = "SCHEDULE_VIEW";
    public static final String SCHEDULE_CREATE = "SCHEDULE_CREATE";
    public static final String SCHEDULE_UPDATE = "SCHEDULE_UPDATE";
    public static final String SCHEDULE_DELETE = "SCHEDULE_DELETE";
    public static final String SCHEDULE_PUBLISH = "SCHEDULE_PUBLISH";
    public static final String SCHEDULE_EXPORT = "SCHEDULE_EXPORT";

    // ── Auto scheduling (M07) ───────────────────────────────────────────
    public static final String AUTO_SCHEDULE_VIEW = "AUTO_SCHEDULE_VIEW";
    public static final String AUTO_SCHEDULE_RUN = "AUTO_SCHEDULE_RUN";
    public static final String AUTO_SCHEDULE_APPLY = "AUTO_SCHEDULE_APPLY";
    public static final String AUTO_SCHEDULE_CONFIG_VIEW = "AUTO_SCHEDULE_CONFIG_VIEW";
    public static final String AUTO_SCHEDULE_CONFIG_EDIT = "AUTO_SCHEDULE_CONFIG_EDIT";

    // ── Leave ───────────────────────────────────────────────────────────
    public static final String LEAVE_VIEW = "LEAVE_VIEW";
    public static final String LEAVE_CREATE = "LEAVE_CREATE";
    public static final String LEAVE_APPROVE = "LEAVE_APPROVE";
    public static final String LEAVE_CANCEL_SELF = "LEAVE_CANCEL_SELF";

    // ── Schedule exchange / swap ────────────────────────────────────────
    public static final String EXCHANGE_VIEW = "EXCHANGE_VIEW";
    public static final String EXCHANGE_CREATE = "EXCHANGE_CREATE";
    public static final String EXCHANGE_APPROVE = "EXCHANGE_APPROVE";
    public static final String EXCHANGE_CANCEL_SELF = "EXCHANGE_CANCEL_SELF";

    // ── Reports / statistics ────────────────────────────────────────────
    public static final String REPORT_VIEW = "REPORT_VIEW";
    public static final String REPORT_EXPORT = "REPORT_EXPORT";

    // ── Holiday ─────────────────────────────────────────────────────────
    public static final String HOLIDAY_VIEW = "HOLIDAY_VIEW";
    public static final String HOLIDAY_CREATE = "HOLIDAY_CREATE";
    public static final String HOLIDAY_UPDATE = "HOLIDAY_UPDATE";
    public static final String HOLIDAY_DELETE = "HOLIDAY_DELETE";

    // ── Notification ────────────────────────────────────────────────────
    public static final String NOTIFICATION_VIEW = "NOTIFICATION_VIEW";
    public static final String NOTIFICATION_CREATE = "NOTIFICATION_CREATE";
    public static final String NOTIFICATION_BROADCAST = "NOTIFICATION_BROADCAST";
    public static final String NOTIFICATION_MANAGE_SELF = "NOTIFICATION_MANAGE_SELF";

    // ── Audit / System log / Config / Integrity ─────────────────────────
    public static final String AUDIT_VIEW = "AUDIT_VIEW";
    public static final String AUDIT_DELETE = "AUDIT_DELETE";
    public static final String SYSTEM_LOG_VIEW = "SYSTEM_LOG_VIEW";
    public static final String APP_CONFIG_VIEW = "APP_CONFIG_VIEW";
    public static final String APP_CONFIG_EDIT = "APP_CONFIG_EDIT";
    public static final String DATA_INTEGRITY_RUN = "DATA_INTEGRITY_RUN";

    // ── Specialty / Shift Type ──────────────────────────────────────────
    public static final String SPECIALTY_MANAGE = "SPECIALTY_MANAGE";
    public static final String SHIFT_TYPE_MANAGE = "SHIFT_TYPE_MANAGE";
    public static final String SCHEDULE_TEMPLATE_MANAGE = "SCHEDULE_TEMPLATE_MANAGE";

    private Permissions() {
        // utility class
    }

    /**
     * Ordered catalog used by the seeder so the order in the database is stable.
     */
    public static Map<String, String> catalog() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(DASHBOARD_VIEW,          "Xem dashboard tổng quan");
        m.put(DASHBOARD_AGGREGATE,     "Xem chỉ số tổng hợp toàn hệ thống");

        m.put(STAFF_VIEW,              "Xem thông tin nhân sự");
        m.put(STAFF_CREATE,            "Tạo nhân sự mới");
        m.put(STAFF_UPDATE,            "Cập nhật thông tin nhân sự");
        m.put(STAFF_DELETE,            "Xóa / vô hiệu hóa nhân sự");
        m.put(STAFF_IMPORT,            "Import nhân sự từ Excel/CSV");

        m.put(ROLE_VIEW,               "Xem ma trận phân quyền");
        m.put(ROLE_EDIT,               "Sửa ma trận phân quyền");

        m.put(PERIOD_VIEW,             "Xem kỳ lịch công tác");
        m.put(PERIOD_CREATE,           "Tạo kỳ lịch công tác");
        m.put(PERIOD_UPDATE,           "Cập nhật kỳ lịch công tác");
        m.put(PERIOD_DELETE,           "Xóa kỳ lịch công tác");
        m.put(PERIOD_PUBLISH,          "Công bố kỳ lịch công tác");
        m.put(PERIOD_ARCHIVE,          "Lưu trữ kỳ lịch công tác");

        m.put(SCHEDULE_VIEW,           "Xem lịch trực (cá nhân/toàn hệ thống)");
        m.put(SCHEDULE_CREATE,         "Tạo lịch trực");
        m.put(SCHEDULE_UPDATE,         "Cập nhật lịch trực");
        m.put(SCHEDULE_DELETE,         "Xóa lịch trực");
        m.put(SCHEDULE_PUBLISH,        "Công bố lịch trực hàng loạt");
        m.put(SCHEDULE_EXPORT,         "Xuất lịch trực ra Excel/PDF");

        m.put(AUTO_SCHEDULE_VIEW,            "Xem phương án auto-schedule");
        m.put(AUTO_SCHEDULE_RUN,             "Chạy auto-schedule");
        m.put(AUTO_SCHEDULE_APPLY,           "Áp dụng phương án auto-schedule");
        m.put(AUTO_SCHEDULE_CONFIG_VIEW,     "Xem cấu hình auto-schedule");
        m.put(AUTO_SCHEDULE_CONFIG_EDIT,     "Sửa cấu hình auto-schedule");

        m.put(LEAVE_VIEW,              "Xem yêu cầu nghỉ phép");
        m.put(LEAVE_CREATE,            "Tạo yêu cầu nghỉ phép");
        m.put(LEAVE_APPROVE,           "Duyệt / từ chối yêu cầu nghỉ phép");
        m.put(LEAVE_CANCEL_SELF,       "Hủy yêu cầu nghỉ phép của chính mình");

        m.put(EXCHANGE_VIEW,           "Xem yêu cầu đổi ca");
        m.put(EXCHANGE_CREATE,         "Tạo yêu cầu đổi ca");
        m.put(EXCHANGE_APPROVE,        "Duyệt / từ chối yêu cầu đổi ca");
        m.put(EXCHANGE_CANCEL_SELF,    "Hủy yêu cầu đổi ca của chính mình");

        m.put(REPORT_VIEW,             "Xem báo cáo thống kê");
        m.put(REPORT_EXPORT,           "Xuất báo cáo");

        m.put(HOLIDAY_VIEW,            "Xem ngày lễ");
        m.put(HOLIDAY_CREATE,          "Tạo ngày lễ");
        m.put(HOLIDAY_UPDATE,          "Cập nhật ngày lễ");
        m.put(HOLIDAY_DELETE,          "Xóa ngày lễ");

        m.put(NOTIFICATION_VIEW,          "Xem thông báo");
        m.put(NOTIFICATION_CREATE,        "Tạo thông báo");
        m.put(NOTIFICATION_BROADCAST,     "Gửi thông báo broadcast tới nhiều người");
        m.put(NOTIFICATION_MANAGE_SELF,   "Quản lý thông báo của chính mình (đánh dấu đã đọc)");

        m.put(AUDIT_VIEW,              "Xem lịch sử thao tác");
        m.put(AUDIT_DELETE,            "Xóa lịch sử thao tác");
        m.put(SYSTEM_LOG_VIEW,         "Xem system log");
        m.put(APP_CONFIG_VIEW,         "Xem cấu hình hệ thống");
        m.put(APP_CONFIG_EDIT,         "Sửa cấu hình hệ thống");
        m.put(DATA_INTEGRITY_RUN,      "Chạy kiểm tra tính toàn vẹn dữ liệu");

        m.put(SPECIALTY_MANAGE,        "Quản lý danh mục chuyên khoa");
        m.put(SHIFT_TYPE_MANAGE,       "Quản lý danh mục loại ca");
        m.put(SCHEDULE_TEMPLATE_MANAGE, "Quản lý mẫu lịch trực");

        return m;
    }

    /**
     * Permissions granted to ADMIN (everything).
     */
    public static Set<String> allPermissions() {
        return catalog().keySet();
    }

    /**
     * Permissions reserved for ADMIN only — MANAGER is denied even though it
     * has nearly everything else.
     */
    public static Set<String> adminOnlyPermissions() {
        return Set.of(
                ROLE_EDIT,
                AUDIT_DELETE,
                SYSTEM_LOG_VIEW,
                DATA_INTEGRITY_RUN,
                AUTO_SCHEDULE_CONFIG_EDIT,
                APP_CONFIG_EDIT
        );
    }

    /**
     * Permissions granted to MANAGER (everything except {@link #adminOnlyPermissions()}).
     */
    public static Set<String> managerPermissions() {
        java.util.Set<String> all = new java.util.LinkedHashSet<>(allPermissions());
        all.removeAll(adminOnlyPermissions());
        return all;
    }

    /**
     * Permissions granted to STAFF — self-service only.
     */
    public static Set<String> staffPermissions() {
        return Set.of(
                DASHBOARD_VIEW,
                STAFF_VIEW,
                SCHEDULE_VIEW,
                HOLIDAY_VIEW,
                LEAVE_VIEW,
                LEAVE_CREATE,
                LEAVE_CANCEL_SELF,
                EXCHANGE_VIEW,
                EXCHANGE_CREATE,
                EXCHANGE_CANCEL_SELF,
                NOTIFICATION_VIEW,
                NOTIFICATION_MANAGE_SELF
        );
    }
}