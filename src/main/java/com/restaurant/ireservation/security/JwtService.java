package com.restaurant.ireservation.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;

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

}
