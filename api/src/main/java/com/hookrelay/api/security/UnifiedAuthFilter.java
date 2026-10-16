package com.hookrelay.api.security;

import com.hookrelay.core.entity.ApiKey;
import com.hookrelay.core.entity.User;
import com.hookrelay.core.repository.UserRepository;
import com.hookrelay.core.service.ApiKeyService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UnifiedAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            String token = resolveToken(request);

            if (StringUtils.hasText(token)) {
                if (token.startsWith("hr_live_")) {
                    // API Key Authentication
                    Optional<ApiKey> apiKeyOpt = apiKeyService.validateApiKey(token);
                    if (apiKeyOpt.isPresent()) {
                        ApiKey apiKey = apiKeyOpt.get();
                        TenantPrincipal tenantPrincipal = new TenantPrincipal(apiKey);
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        tenantPrincipal,
                                        null,
                                        tenantPrincipal.getAuthorities()
                                );
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } else {
                    // JWT Authentication
                    Claims claims = jwtTokenProvider.validateAndParseClaims(token);
                    if (claims != null && "ACCESS".equals(claims.get("type", String.class))) {
                        UUID userId = UUID.fromString(claims.getSubject());
                        Optional<User> userOpt = userRepository.findById(userId);
                        if (userOpt.isPresent()) {
                            UserPrincipal userPrincipal = new UserPrincipal(userOpt.get());
                            UsernamePasswordAuthenticationToken authentication =
                                    new UsernamePasswordAuthenticationToken(
                                            userPrincipal,
                                            null,
                                            userPrincipal.getAuthorities()
                                    );
                            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        String apiKeyHeader = request.getHeader("X-API-Key");
        if (StringUtils.hasText(apiKeyHeader)) {
            return apiKeyHeader;
        }
        return null;
    }
}
