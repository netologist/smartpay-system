package com.hozgan.smartpay.gateway.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes RFC 7807 problem-detail JSON to a filter-level response.
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailsWriter {

    public static final String CONTENT_TYPE = "application/problem+json";

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, ProblemDetails problem) throws IOException {
        response.setStatus(problem.status());
        response.setContentType(CONTENT_TYPE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
