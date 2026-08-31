package dev.techmart.identity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.techmart.common.error.ApiError;
import dev.techmart.common.error.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Doc Bearer token moi request va dat AuthPrincipal vao SecurityContext.
 *
 * <p>Luu y: ngoai le nem trong filter <b>khong</b> di qua @RestControllerAdvice
 * (filter chay truoc DispatcherServlet), nen phai tu ghi envelope loi o day
 * de client luon nhan dung mot cau truc.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            chain.doFilter(request, response);   // endpoint public van di tiep
            return;
        }

        try {
            AuthPrincipal principal = jwtService.parseAccessToken(header.substring(PREFIX.length()).trim());
            var auth = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority(principal.role().authority())));
            SecurityContextHolder.getContext().setAuthentication(auth);
            chain.doFilter(request, response);

        } catch (BusinessException e) {
            SecurityContextHolder.clearContext();
            writeError(request, response, e);
        }
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response, BusinessException e)
            throws IOException {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        response.setStatus(e.code().status().value());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of(e.code(), e.getMessage(), request.getRequestURI(), traceId, null));
    }
}
