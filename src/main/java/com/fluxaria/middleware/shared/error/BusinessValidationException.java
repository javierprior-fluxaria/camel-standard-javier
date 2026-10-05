package com.fluxaria.middleware.shared.error;

import java.util.Collections;
import java.util.List;

/**
 * Excepción de validación de negocio.
 * Mapeada automáticamente por BaseRouteBuilder a HTTP 400 Bad Request RFC 7807.
 */
public class BusinessValidationException extends RuntimeException {

    private final List<String> validationErrors;

    public BusinessValidationException(String message) {
        super(message);
        this.validationErrors = Collections.singletonList(message);
    }

    public BusinessValidationException(String message, List<String> validationErrors) {
        super(message);
        this.validationErrors = validationErrors != null ? List.copyOf(validationErrors) : Collections.emptyList();
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }
}