package com.hookrelay.api.service;

import com.hookrelay.api.dto.AuthDtos.AuthResponse;
import com.hookrelay.api.dto.AuthDtos.LoginRequest;
import com.hookrelay.api.dto.AuthDtos.SignupRequest;
import com.hookrelay.api.security.JwtTokenProvider;
import com.hookrelay.core.entity.Org;
import com.hookrelay.core.entity.Project;
import com.hookrelay.core.entity.Role;
import com.hookrelay.core.entity.User;
import com.hookrelay.core.repository.OrgRepository;
import com.hookrelay.core.repository.ProjectRepository;
import com.hookrelay.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private OrgRepository orgRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    private Org testOrg;
    private User testUser;
    private Project testProject;

    @BeforeEach
    void setUp() {
        testOrg = Org.builder().name("Acme Corp").slug("acme-corp").build();
        testOrg.setId(UUID.randomUUID());

        testUser = User.builder()
                .org(testOrg)
                .email("alice@acme.com")
                .fullName("Alice Smith")
                .passwordHash("hashed_secret")
                .role(Role.ADMIN)
                .build();
        testUser.setId(UUID.randomUUID());

        testProject = Project.builder()
                .org(testOrg)
                .name("Production")
                .rateLimitPerSecond(100)
                .build();
        testProject.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Signup successfully creates org, user, default project and returns JWT")
    void testSignupSuccess() {
        SignupRequest req = new SignupRequest("alice@acme.com", "password123!", "Alice Smith", "Acme Corp");

        when(userRepository.existsByEmail("alice@acme.com")).thenReturn(false);
        when(orgRepository.existsBySlug("acme-corp")).thenReturn(false);
        when(orgRepository.save(any(Org.class))).thenReturn(testOrg);
        when(passwordEncoder.encode("password123!")).thenReturn("hashed_secret");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(projectRepository.save(any(Project.class))).thenReturn(testProject);
        when(jwtTokenProvider.generateAccessToken(testUser)).thenReturn("mock_access_token");
        when(jwtTokenProvider.generateRefreshToken(testUser)).thenReturn("mock_refresh_token");

        AuthResponse res = authService.signup(req);

        assertNotNull(res);
        assertEquals("mock_access_token", res.token());
        assertEquals("alice@acme.com", res.user().email());
        assertEquals(testProject.getId(), res.defaultProjectId());

        verify(orgRepository).save(any(Org.class));
        verify(userRepository).save(any(User.class));
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    @DisplayName("Signup rejects duplicate email")
    void testSignupDuplicateEmail() {
        SignupRequest req = new SignupRequest("alice@acme.com", "password123!", "Alice Smith", "Acme Corp");
        when(userRepository.existsByEmail("alice@acme.com")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> authService.signup(req));
        verify(orgRepository, never()).save(any());
    }

    @Test
    @DisplayName("Login with valid credentials returns tokens and user profile")
    void testLoginSuccess() {
        LoginRequest req = new LoginRequest("alice@acme.com", "password123!");

        when(userRepository.findByEmail("alice@acme.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123!", "hashed_secret")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(testUser)).thenReturn("mock_access_token");
        when(jwtTokenProvider.generateRefreshToken(testUser)).thenReturn("mock_refresh_token");
        when(projectRepository.findByOrgId(testOrg.getId())).thenReturn(List.of(testProject));

        AuthResponse res = authService.login(req);

        assertNotNull(res);
        assertEquals("mock_access_token", res.token());
        assertEquals("Alice Smith", res.user().fullName());
    }

    @Test
    @DisplayName("Login rejects incorrect password")
    void testLoginIncorrectPassword() {
        LoginRequest req = new LoginRequest("alice@acme.com", "wrongpass");

        when(userRepository.findByEmail("alice@acme.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongpass", "hashed_secret")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.login(req));
    }
}
