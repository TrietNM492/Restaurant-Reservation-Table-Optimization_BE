package com.restaurant.ireservation.security;

import com.restaurant.ireservation.domain.entity.User;
import com.restaurant.ireservation.dto.response.UserSecurityRes;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class CustomUserDetails implements UserDetails {
    UserSecurityRes user;

    public CustomUserDetails(UserSecurityRes user) {
        this.user = user;
    }
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        HashSet<GrantedAuthority> authorities = new HashSet<>();

        if (user.getRole() != null) {
            var permissions = user.getPermission();
            authorities.add(new SimpleGrantedAuthority(user.getRole()));
            permissions.stream().map(SimpleGrantedAuthority::new)
                    .forEach(authorities::add);
        }
        return authorities;
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }
}
