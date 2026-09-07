package com.hozgan.smartpay.gateway.web;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * RFC 7807-style problem detail body emitted for every edge error response
 * (shape fixed by the STORY-006 gateway payload contracts).
 */
public record ProblemDetails(
        @JsonProperty("type") String type,
        @JsonProperty("title") String title,
        @JsonProperty("status") int status,
        @JsonProperty("detail") String detail,
        @JsonProperty("timestamp") Instant timestamp
) {

    public static ProblemDetails of(String type, String title, int status, String detail) {
        return new ProblemDetails(type, title, status, detail, Instant.now());
    }
}
