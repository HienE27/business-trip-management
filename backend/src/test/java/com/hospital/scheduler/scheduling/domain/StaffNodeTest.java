package com.hospital.scheduler.scheduling.domain;

import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.entity.Staff;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StaffNode.
 */
class StaffNodeTest {

    @Test
    void testFromStaff_NgoaiSpecialty() {
        // Given
        Staff staff = createStaff(1, "BS. Nguyễn Văn A", "Ngoại", true);

        // When
        StaffNode node = StaffNode.from(staff);

        // Then
        assertEquals(1, node.getId());
        assertEquals("BS. Nguyễn Văn A", node.getFullName());
        assertEquals("Ngoại", node.getSpecialtyName());
        assertTrue(node.isActive());
        assertTrue(node.isEligibleFor("L01"));
        assertTrue(node.isEligibleFor("L02"));
        assertTrue(node.isEligibleFor("L03"));
        assertTrue(node.isEligibleFor("L04"));
    }

    @Test
    void testFromStaff_NoiSpecialty() {
        // Given
        Staff staff = createStaff(2, "BS. Trần Thị B", "Nội", true);

        // When
        StaffNode node = StaffNode.from(staff);

        // Then
        assertTrue(node.isEligibleFor("L01"));
        assertTrue(node.isEligibleFor("L02"));
        assertTrue(node.isEligibleFor("L03"));
        assertTrue(node.isEligibleFor("L04"));
    }

    @Test
    void testFromStaff_SanSpecialty_L04Only() {
        // Given
        Staff staff = createStaff(3, "BS. Lê Văn C", "Sản", true);

        // When
        StaffNode node = StaffNode.from(staff);

        // Then
        assertFalse(node.isEligibleFor("L01"));
        assertFalse(node.isEligibleFor("L02"));
        assertFalse(node.isEligibleFor("L03"));
        assertTrue(node.isEligibleFor("L04"));
    }

    @Test
    void testFromStaff_InactiveStaff() {
        // Given
        Staff staff = createStaff(4, "BS. Hoàng Văn D", "Ngoại", false);

        // When
        StaffNode node = StaffNode.from(staff);

        // Then
        assertFalse(node.isActive());
        assertTrue(node.getEligibleShiftTypes().isEmpty());
    }

    @Test
    void testIsEligibleForL04WithSpecialty() {
        // Given
        Staff staff = createStaff(5, "BS. Phạm Thị E", "Ngoại", true);
        StaffNode node = StaffNode.from(staff);

        // When/Then
        assertTrue(node.isEligibleFor("L04", 1)); // Matching specialty
    }

    @Test
    void testFromNullStaff() {
        assertThrows(IllegalArgumentException.class, () -> StaffNode.from(null));
    }

    private Staff createStaff(int id, String name, String specialtyName, boolean active) {
        Staff staff = new Staff();
        staff.setId(id);
        staff.setFullName(name);
        staff.setIsActive(active);
        
        Specialty specialty = new Specialty();
        specialty.setId(1);
        specialty.setName(specialtyName);
        staff.setSpecialty(specialty);
        
        return staff;
    }
}
