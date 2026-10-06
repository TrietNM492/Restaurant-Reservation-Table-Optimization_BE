package com.restaurant.ireservation.security;

import com.restaurant.ireservation.dto.response.ResLoginDTO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;

@Service
public class JwtService {
    private final JwtEncoder jwtEncoder;
    public static final MacAlgorithm JWT_ALGO;

    @Value("${ijres.jwt.secret}")
    private String jwtKey;

    @Value("${ijres.jwt.access-token-validity-sec}")
    private Long accessTokenExpiration;

    @Value("${ijres.jwt.refresh-token-validity-sec}")
    private Long refreshTokenExpiration;
    private final SecretKey jwtSecretKey;

    static {
        JWT_ALGO = MacAlgorithm.HS256;
    }

    public JwtService(JwtEncoder jwtEncoder, SecretKey jwtSecretKey) {
        this.jwtEncoder = jwtEncoder;
        this.jwtSecretKey = jwtSecretKey;
    }

    public String createAccessToken(String username, ResLoginDTO dto, String sessionId, Set<String> permissions) {
        ResLoginDTO.UserInsideToken userInsideToken = new ResLoginDTO.UserInsideToken();
        userInsideToken.setId(dto.getUser().getId());
        userInsideToken.setUsername(username);
        userInsideToken.setName(dto.getUser().getName());
        Instant now = Instant.now();
        Instant validity = now.plus(this.accessTokenExpiration, ChronoUnit.SECONDS);
        JwtClaimsSet claim = JwtClaimsSet.builder()
                .issuedAt(now)
                .expiresAt(validity)
                .subject(username)
                .claim("user", userInsideToken)
                .claim("permissions", permissions)
                .claim("sessionId", sessionId).build();
        JwsHeader jwsHeader = JwsHeader.with(JWT_ALGO).build();
        return this.jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claim)).getTokenValue();
    }

    public String createRefreshToken(ResLoginDTO.UserLogin userLogin) {
        var userToken = new ResLoginDTO.UserInsideToken();
        userToken.setId(userToken.getId());
        userToken.setUsername(userLogin.getUsername());
        Instant now = Instant.now();
        Instant validity = now.plus(refreshTokenExpiration, ChronoUnit.SECONDS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuedAt(now)
                .expiresAt(validity)
                .subject(userLogin.getUsername())
                .claim("user", userToken)
                .build();
        JwsHeader jwsHeader = JwsHeader.with(JWT_ALGO).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }

    public Claims parseRefreshTokenIgnoreExpire(String token) {
        try {
            return Jwts.parser().verifyWith(jwtSecretKey).build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }

    public Claims parseRefreshToken(String token) {
        return Jwts.parser().verifyWith(jwtSecretKey).build().parseSignedClaims(token).getPayload();
    }

    private static String extractPrincipal(Authentication authentication) {
        if (authentication == null) {
            return null;
        } else {
            Object var2;
            if ((var2 = authentication.getPrincipal()) instanceof UserDetails) {
                UserDetails springSecurityUser = (UserDetails) var2;
                return springSecurityUser.getUsername();
            } else {
                Object var4;
                if ((var4 = authentication.getPrincipal()) instanceof Jwt) {
                    Jwt jwt = (Jwt) var4;
                    return jwt.getSubject();
                } else {
                    Object var6;
                    if ((var6 = authentication.getPrincipal()) instanceof String) {
                        String s = (String) var6;
                        return s;
                    } else {
                        return null;
                    }
                }
            }
        }
    }

    public static Optional<String> getCurrentUser() {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        return  Optional.ofNullable(extractPrincipal(securityContext.getAuthentication()));
    }

}
