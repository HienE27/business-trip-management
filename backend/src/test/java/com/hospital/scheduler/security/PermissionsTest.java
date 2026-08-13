package com.hospital.scheduler.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guard tests for {@link Permissions}. The catalog is the single source of
 * truth shared with the seeder and the frontend, so we lock it down with a
 * small set of invariants:
 *
 * <ul>
 *   <li>Every public constant resolves to a non-empty name in the catalog.</li>
 *   <li>ADMIN == all permissions.</li>
 *   <li>MANAGER ⊆ ALL and STAFF ⊆ MANAGER.</li>
 *   <li>MANAGER không có quyền chỉ-dành-cho-ADMIN (định nghĩa trong {@link #managerReservedForAdmin()}).</li>
 *   <li>STAFF không có quyền chỉ-dành-cho-MANAGER/ADMIN.</li>
 *   <li>STAFF có STAFF_VIEW_ALL (xem lịch toàn phòng tại M06-F01/F02).</li>
 *   <li>MANAGER có STAFF_VIEW_ALL.</li>
 * </ul>
 */
class PermissionsTest {

    /** Permissions chỉ ADMIN mới được phép có (theo tài liệu M01-F05). */
    private static java.util.Set<String> managerReservedForAdmin() {
        return java.util.Set.of(
                Permissions.ROLE_EDIT,
                Permissions.AUDIT_DELETE,
                Permissions.DATA_INTEGRITY_RUN,
                Permissions.AUTO_SCHEDULE_CONFIG_EDIT,
                Permissions.APP_CONFIG_EDIT,
                Permissions.STAFF_CREATE,
                Permissions.STAFF_UPDATE,
                Permissions.STAFF_DELETE,
                Permissions.STAFF_IMPORT,
                Permissions.PERIOD_CREATE,
                Permissions.PERIOD_UPDATE,
                Permissions.PERIOD_DELETE,
                Permissions.PERIOD_PUBLISH,
                Permissions.PERIOD_ARCHIVE,
                Permissions.HOLIDAY_CREATE,
                Permissions.HOLIDAY_UPDATE,
                Permissions.HOLIDAY_DELETE,
                Permissions.NOTIFICATION_CREATE,
                Permissions.NOTIFICATION_BROADCAST,
                Permissions.SPECIALTY_MANAGE,
                Permissions.SHIFT_TYPE_MANAGE,
                Permissions.SCHEDULE_TEMPLATE_MANAGE
        );
    }

    @Test
    void everyConstantIsInCatalog() {
        Set<String> catalog = Permissions.catalog().keySet();
        for (java.lang.reflect.Field f : Permissions.class.getDeclaredFields()) {
            if (f.getType() != String.class) continue;
            try {
                Object value = f.get(null);
                assertNotNull(value, "Permission constant " + f.getName() + " is null");
                assertTrue(catalog.contains(value),
                        "Permission " + value + " (" + f.getName() + ") missing from catalog()");
                assertFalse(((String) value).isBlank(),
                        "Permission constant " + f.getName() + " is blank");
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    void adminGetsAllPermissions() {
        Set<String> all = Permissions.allPermissions();
        assertTrue(all.containsAll(Permissions.managerPermissions()));
        assertTrue(all.containsAll(Permissions.staffPermissions()));
    }

    @Test
    void managerIsSubsetOfAllAndNotAdminOnly() {
        Set<String> all = Permissions.allPermissions();
        Set<String> manager = Permissions.managerPermissions();
        assertTrue(all.containsAll(manager),
                "MANAGER permissions must be a subset of ALL permissions");

        for (String p : managerReservedForAdmin()) {
            assertFalse(manager.contains(p),
                    "MANAGER must not have ADMIN-only permission " + p);
        }
    }

    @Test
    void managerHasStaffViewAll() {
        assertTrue(Permissions.managerPermissions().contains(Permissions.STAFF_VIEW_ALL),
                "MANAGER must have STAFF_VIEW_ALL to see the staff directory");
    }

    @Test
    void staffIsSubsetOfManager() {
        Set<String> manager = Permissions.managerPermissions();
        assertTrue(manager.containsAll(Permissions.staffPermissions()));
    }

    @Test
    void staffCanSeeFullStaffDirectory() {
        // M06-F01/F02: nhân viên được phép xem lịch toàn phòng (read-only).
        // Cần STAFF_VIEW_ALL để hiển thị cột nhân sự trên bảng lịch tháng.
        assertTrue(Permissions.staffPermissions().contains(Permissions.STAFF_VIEW_ALL),
                "STAFF must have STAFF_VIEW_ALL to see the staff directory and full schedule");
        assertTrue(Permissions.staffPermissions().contains(Permissions.STAFF_VIEW_SELF),
                "STAFF must have STAFF_VIEW_SELF to view their own profile");
    }

    @Test
    void staffNeverIncludesManagerReservedPermissions() {
        for (String p : Permissions.staffPermissions()) {
            assertFalse(managerReservedForAdmin().contains(p),
                    "STAFF must not include ADMIN/MANAGER-reserved permission " + p);
        }
    }

    @Test
    void catalogHasAtLeastOneDescriptionPerKey() {
        var catalog = Permissions.catalog();
        for (var entry : catalog.entrySet()) {
            assertNotNull(entry.getValue(), "Description for " + entry.getKey() + " is null");
            assertFalse(entry.getValue().isBlank(),
                    "Description for " + entry.getKey() + " is blank");
        }
    }
}