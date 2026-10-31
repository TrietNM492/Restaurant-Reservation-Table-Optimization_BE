package com.restaurant.ireservation.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.util.Base64;
import com.restaurant.ireservation.security.GuestAwareJwtDecoder;
import com.restaurant.ireservation.security.JwtService;
import com.restaurant.ireservation.util.constants.Constants;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

import javax.crypto.SecretKey;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${ijres.jwt.secret}")
    private String jwtKey;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity httpSecurity,
            CorsConfigurationSource corsConfigurationSource,
            JwtDecoder decoder,
            HttpServletRequest request,
            JwtAuthenticationConverter converter
    ) throws Exception {
        String[] whiteList = {
                "/error",
                "/storage/**",
                "/api/v1/search/**",
                "/ws/**",
                "/api/v1/files/**",
                "/api/v1/payment/webhook",
                "/api/v1/payment/vnpay-return",
                "/api/v1/payments/vnpay-ipn",
                "/api/v1/payments/vnpay-return",
                "/api/v1/payment/*/invoice/download",
                "/api/v1/payments/*/invoice/download",
                "/api/v1/kitchen/**",
                "/api/v1/predictions/**",
                "/v3/api-docs",
                "/swagger-ui/index.html"
        };
        List<String> publishEndpoints = List.of(Constants.VERSION_API + "/home",
                Constants.VERSION_API + "/auth/register",
                Constants.VERSION_API + "/auth/login",
                Constants.VERSION_API + "/auth/refresh",
                Constants.VERSION_API + "/account/activate/**",
                Constants.VERSION_API + "/account/reset-password/init",
                Constants.VERSION_API + "/account/reset-password/finish",
                Constants.VERSION_API + "/files/**",
                Constants.VERSION_API + "/menus/**",
                Constants.VERSION_API + "/payment/webhook",
                Constants.VERSION_API + "/payment/vnpay-return");
        httpSecurity
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(
                        auth ->
                                auth
                                        // guest
                                        .requestMatchers(HttpMethod.GET, Constants.VERSION_API + "/home").permitAll()
                                        .requestMatchers(HttpMethod.GET, Constants.VERSION_API + "/menus/**").permitAll()
                                        .requestMatchers(HttpMethod.POST, Constants.VERSION_API + "/auth/register")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.POST, Constants.VERSION_API + "/auth/login").permitAll()


                                        // user
                                        .requestMatchers(HttpMethod.POST, Constants.VERSION_API + "/auth/refresh").permitAll()
                                        .requestMatchers(HttpMethod.POST,
                                                Constants.VERSION_API + "/account/reset-password/init")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.POST,
                                                Constants.VERSION_API + "/account/reset-password/finish")
                                        .permitAll()
                                        .requestMatchers(whiteList).permitAll()
                                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwtConfigurer -> jwtConfigurer.jwtAuthenticationConverter(converter)
                                .decoder(new GuestAwareJwtDecoder(decoder, request, publishEndpoints))
                        ))
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));

        return httpSecurity.build();

    }


    @Bean
    public NimbusJwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey()));
    }

    @Bean
    public SecretKey jwtSecretKey() {
        byte[] keyBytes = Base64.from(jwtKey).decode();
        return Keys.hmacShaKeyFor(keyBytes);
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey())
                .macAlgorithm(JwtService.JWT_ALGO).build();
        return token -> {
            try {
                return jwtDecoder.decode(token);
            } catch (Exception e) {
                System.out.println(">>> JWT Error: " + e.getMessage());
                throw e;
            }
        };
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter(CustomJwtAuthenticationConverter converter) {

        converter.setAuthorityPrefix("");
        converter.setAuthoritiesClaimName("permissions");
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(converter);
        return jwtAuthenticationConverter;
    }

}
