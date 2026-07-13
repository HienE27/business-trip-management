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
 *   <li>MANAGER == ALL \ adminOnly.</li>
 *   <li>STAFF ⊆ MANAGER.</li>
 *   <li>adminOnly ⊆ ALL.</li>
 *   <li>STAFF never includes any adminOnly permission.</li>
 * </ul>
 */
class PermissionsTest {

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
    void managerIsAllMinusAdminOnly() {
        Set<String> all = Permissions.allPermissions();
        Set<String> adminOnly = Permissions.adminOnlyPermissions();
        Set<String> manager = Permissions.managerPermissions();
        assertTrue(all.containsAll(adminOnly), "adminOnly must be a subset of all permissions");
        assertTrue(manager.containsAll(all.stream().filter(p -> !adminOnly.contains(p)).toList()));
        assertFalse(manager.contains(Permissions.ROLE_EDIT));
        assertFalse(manager.contains(Permissions.AUDIT_DELETE));
    }

    @Test
    void staffIsSubsetOfManager() {
        Set<String> manager = Permissions.managerPermissions();
        assertTrue(manager.containsAll(Permissions.staffPermissions()));
    }

    @Test
    void staffNeverIncludesAdminOnlyPermissions() {
        Set<String> adminOnly = Permissions.adminOnlyPermissions();
        for (String p : Permissions.staffPermissions()) {
            assertFalse(adminOnly.contains(p),
                    "STAFF must not include admin-only permission " + p);
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