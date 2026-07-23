package com.hospital.scheduler.security;

import com.hospital.scheduler.entity.Staff;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * Principal representing the authenticated user.
 * Implements Spring Security's UserDetails interface.
 */
@Getter
@AllArgsConstructor
class CurrentUser implements UserDetails {

    private final Staff staff;
    private final String username;
    private final Collection<? extends GrantedAuthority> authorities;

    public CurrentUser(Staff staff) {
        this.staff = staff;
        this.username = staff.getUsername();
        this.authorities = Collections.emptyList();
    }

    @Override
    public String getPassword() {
        return staff.getPasswordHash();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(staff.getIsActive());
    }
}
