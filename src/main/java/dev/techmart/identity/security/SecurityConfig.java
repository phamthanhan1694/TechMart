package dev.techmart.identity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.techmart.common.config.AppProperties;
import dev.techmart.common.error.ApiError;
import dev.techmart.common.error.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Cau hinh bao mat.
 *
 * <p>Nguyen tac: <b>mac dinh la tu choi</b> - {@code anyRequest().authenticated()},
 * chi mo ra tung endpoint public mot cach tuong minh. Cach nguoc lai (mac dinh mo,
 * chan tung cai) chac chan se bo sot khi them endpoint moi.
 *
 * <p>Phan quyen theo quyen so huu (don hang nay co phai cua user nay khong) nam o
 * tang service, khong o day - @PreAuthorize chi kiem duoc role.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final ObjectMapper objectMapper;
    private final AppProperties props;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter, ObjectMapper objectMapper, AppProperties props) {
        this.jwtFilter = jwtFilter;
        this.objectMapper = objectMapper;
        this.props = props;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // API stateless dung Bearer token -> khong co session, khong can CSRF token
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh")
                            .permitAll()
                        // Webhook khong co token nhung phai xac thuc chu ky HMAC o tang service
                        .requestMatchers("/api/v1/payments/webhook").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/products/**",
                                "/api/v1/categories/**",
                                "/api/v1/brands/**",
                                "/api/v1/flash-sales/active",
                                "/api/v1/flash-sales/upcoming",
                                "/api/v1/serials/*/warranty").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                write(res, req.getRequestURI(), ErrorCode.UNAUTHENTICATED))
                        .accessDeniedHandler((req, res, e) ->
                                write(res, req.getRequestURI(), ErrorCode.ACCESS_DENIED)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** Strength 12: cham hon mac dinh 10 mot chut, doi lai chi phi tan cong brute force cao hon. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Whitelist cu the, khong dung "*" - va khong the dung "*" khi allowCredentials(true)
        config.setAllowedOrigins(props.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        config.setExposedHeaders(List.of("X-RateLimit-Limit", "X-RateLimit-Remaining", "Retry-After"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private void write(jakarta.servlet.http.HttpServletResponse res, String path, ErrorCode code) throws IOException {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        res.setStatus(code.status().value());
        res.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(res.getOutputStream(),
                ApiError.of(code, code.defaultMessage(), path, traceId, null));
    }
}
