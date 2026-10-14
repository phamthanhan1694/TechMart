package dev.techmart.identity.security;

import tools.jackson.databind.ObjectMapper;
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
 * Cấu hinh báo mat.
 *
 * <p>Nguyên tắc: <b>mac dinh là từ choi</b> - {@code anyRequest().authenticated()},
 * chỉ mo ra tung endpoint public một cach tuong minh. Cach nguoc lai (mac dinh mo,
 * chặn tung cai) chac chặn sẽ bo sot khi thêm endpoint mọi.
 *
 * <p>Phan quyen theo quyền sở hữu (đơn hàng này có phải của user này không) nằm ở
 * tầng service, không ở đây - @PreAuthorize chỉ kiểm được role.
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
                // API stateless dùng Bearer token -> không có session, không cần CSRF token
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh")
                            .permitAll()
                        // Webhook không có token nhưng phải xac thực chu ky HMAC o tầng service
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

    /** Strength 12: cham hơn mac dinh 10 một chut, đổi lại chỉ phi tan cong brute force cao hơn. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Whitelist cụ thể, không dùng "*" - và không the dùng "*" khi allowCredentials(true)
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
