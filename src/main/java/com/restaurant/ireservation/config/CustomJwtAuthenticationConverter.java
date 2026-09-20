package com.restaurant.ireservation.config;

import com.restaurant.ireservation.security.SessionManager;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class CustomJwtAuthenticationConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final SessionManager sessionManager;

    @Setter
    private String authorityPrefix = "";

    @Setter
    private String authoritiesClaimName = "permissions";

    public CustomJwtAuthenticationConverter(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public @Nullable Collection<GrantedAuthority> convert(Jwt source) {

        String username = source.getSubject();
        String sessionId = source.getClaimAsString("sessionId");

        if (username != null & sessionId != null) {
            boolean isValidSession = sessionManager.isValidSession(username, sessionId);

            if (!isValidSession) {
                throw new BadCredentialsException("Session expired or invalid. Please re-authenticate.");
            }
        }
        Collection<String> authorities = Collections.singleton(source.getClaimAsString(authoritiesClaimName));

        return authorities.stream()
                .map(authotiry -> new SimpleGrantedAuthority(authorityPrefix + authotiry))
                .collect(Collectors.toSet());
    }
}
