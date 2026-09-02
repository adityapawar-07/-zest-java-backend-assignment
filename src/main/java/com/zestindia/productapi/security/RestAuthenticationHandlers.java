package com.zestindia.productapi.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestindia.productapi.exception.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Ensures unauthenticated (401) and unauthorized (403) responses use the
 * same JSON ErrorResponse shape as the rest of the API instead of Spring
 * Security's default HTML/plain-text error pages.
 */
@Component
public class RestAuthenticationHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    // Reuses the same Spring-Boot-configured ObjectMapper bean (ISO-8601 dates
    // via JavaTimeModule) that the rest of the app's JSON responses use -
    // avoids the timestamp-as-number-array format a plain `new ObjectMapper()` gives.
    public RestAuthenticationHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        writeError(response, request, HttpStatus.UNAUTHORIZED, "Authentication is required or the token is invalid/expired");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                        AccessDeniedException accessDeniedException) throws IOException, ServletException {
        writeError(response, request, HttpStatus.FORBIDDEN, "You do not have permission to perform this action");
    }

    private void writeError(HttpServletResponse response, HttpServletRequest request,
                             HttpStatus status, String message) throws IOException {
        ErrorResponse error = new ErrorResponse(status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
