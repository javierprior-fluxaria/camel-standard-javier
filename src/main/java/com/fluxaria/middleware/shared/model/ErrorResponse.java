package com.fluxaria.middleware.shared.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * DTO canónico de error conforme al estándar RFC 7807 (Problem Details for HTTP APIs).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String correlationId,
        Instant timestamp,
        List<String> invalidParams
) {
    public static ErrorResponse of(String type, String title, int status, String detail, String instance, String correlationId) {
        return new ErrorResponse(type, title, status, detail, instance, correlationId, Instant.now(), null);
    }

    public static ErrorResponse ofValidation(String detail, String instance, String correlationId, List<String> invalidParams) {
        return new ErrorResponse(
                "https://api.fluxaria.com/errors/validation-error",
                "Business Validation Error",
                400,
                detail,
                instance,
                correlationId,
                Instant.now(),
                invalidParams
        );
    }
}