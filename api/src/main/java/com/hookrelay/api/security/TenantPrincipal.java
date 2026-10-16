package com.hookrelay.api.security;

import com.hookrelay.core.entity.ApiKey;
import com.hookrelay.core.entity.Project;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Getter
public class TenantPrincipal implements UserDetails {

    private final ApiKey apiKey;
    private final Project project;
    private final Collection<? extends GrantedAuthority> authorities;

    public TenantPrincipal(ApiKey apiKey) {
        this.apiKey = apiKey;
        this.project = apiKey.getProject();
        this.authorities = List.of(
                new SimpleGrantedAuthority("ROLE_TENANT"),
                new SimpleGrantedAuthority("ROLE_INGEST")
        );
    }

    public UUID getProjectId() {
        return project.getId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return apiKey.getName();
    }

    @Override
    public boolean isAccountNonExpired() {
        return !apiKey.isRevoked();
    }

    @Override
    public boolean isAccountNonLocked() {
        return !apiKey.isRevoked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return !apiKey.isRevoked();
    }
}
