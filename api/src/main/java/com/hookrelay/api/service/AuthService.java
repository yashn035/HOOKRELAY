package com.hookrelay.api.service;

import com.hookrelay.api.dto.AuthDtos.*;
import com.hookrelay.api.security.JwtTokenProvider;
import com.hookrelay.core.entity.Org;
import com.hookrelay.core.entity.Project;
import com.hookrelay.core.entity.Role;
import com.hookrelay.core.entity.User;
import com.hookrelay.core.repository.OrgRepository;
import com.hookrelay.core.repository.ProjectRepository;
import com.hookrelay.core.repository.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final OrgRepository orgRepository;
    private final ProjectRepository projectRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(
            UserRepository userRepository,
            OrgRepository orgRepository,
            ProjectRepository projectRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider
    ) {
        this.userRepository = userRepository;
        this.orgRepository = orgRepository;
        this.projectRepository = projectRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public AuthResponse signup(SignupRequest req) {
        if (userRepository.existsByEmail(req.email().toLowerCase(Locale.ROOT).trim())) {
            throw new IllegalArgumentException("User with this email already exists");
        }

        // Generate unique slug
        String baseSlug = req.orgName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "-");
        if (baseSlug.isBlank()) baseSlug = "org";
        String slug = baseSlug;
        int counter = 1;
        while (orgRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + counter++;
        }

        Org org = Org.builder()
                .name(req.orgName().trim())
                .slug(slug)
                .build();
        Org savedOrg = orgRepository.save(org);

        User user = User.builder()
                .org(savedOrg)
                .email(req.email().toLowerCase(Locale.ROOT).trim())
                .fullName(req.fullName().trim())
                .passwordHash(passwordEncoder.encode(req.password()))
                .role(Role.ADMIN)
                .build();
        User savedUser = userRepository.save(user);

        // Create default project
        Project project = Project.builder()
                .org(savedOrg)
                .name("Production")
                .description("Default production project")
                .rateLimitPerSecond(100)
                .build();
        Project savedProject = projectRepository.save(project);

        String token = jwtTokenProvider.generateAccessToken(savedUser);
        String refreshToken = jwtTokenProvider.generateRefreshToken(savedUser);

        UserSummary summary = new UserSummary(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getFullName(),
                savedUser.getRole().name(),
                savedOrg.getId(),
                savedOrg.getName()
        );

        return new AuthResponse(token, refreshToken, summary, savedProject.getId());
    }

    public AuthResponse login(LoginRequest req) {
        String email = req.email().toLowerCase(Locale.ROOT).trim();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        String token = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        List<Project> projects = projectRepository.findByOrgId(user.getOrg().getId());
        UUID defaultProjectId = projects.isEmpty() ? null : projects.get(0).getId();

        UserSummary summary = new UserSummary(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getOrg().getId(),
                user.getOrg().getName()
        );

        return new AuthResponse(token, refreshToken, summary, defaultProjectId);
    }

    public String refreshToken(String refreshToken) {
        Claims claims = jwtTokenProvider.validateAndParseClaims(refreshToken);
        if (claims == null || !"REFRESH".equals(claims.get("type", String.class))) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }

        UUID userId = UUID.fromString(claims.getSubject());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        return jwtTokenProvider.generateAccessToken(user);
    }

    public UserSummary getCurrentUserSummary(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return new UserSummary(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getOrg().getId(),
                user.getOrg().getName()
        );
    }
}
