package com.restaurant.ireservation.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class GuestAwareJwtDecoder implements JwtDecoder {

    final JwtDecoder jwtDecoder;
    final HttpServletRequest request;
    final List<String> publicAntPatterns;
    @Override
    public Jwt decode(String token) throws JwtException {
        try {
            return jwtDecoder.decode(token);
        } catch (JwtException e) {
            org.springframework.util.AntPathMatcher pathMatcher = new org.springframework.util.AntPathMatcher();

            String currentPath = request.getRequestURI();
            boolean isPublishEndPoint = publicAntPatterns.stream()
                    .anyMatch(pattern -> pathMatcher.match(pattern, currentPath));
            if (isPublishEndPoint) {
                return Jwt.withTokenValue("anonymous_token")
                        .header("alg", "none")
                        .subject("anonymousUser")
                        .claim("scope", "")
                        .expiresAt(Instant.MAX)
                        .issuedAt(Instant.now())
                        .build();
            }
            throw e;
        }
    }
}
