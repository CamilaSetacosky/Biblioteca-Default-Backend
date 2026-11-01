package com.EduardoMango.Biblioteca.exception;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Safe application error: never retains provider bodies, URLs or credentials. */
public class GoogleBooksException extends RuntimeException {
    public enum Failure {
        QUOTA_EXCEEDED(503, "Google Books alcanzó su límite de consultas. Podés continuar con la carga manual."),
        CONFIGURATION_ERROR(503, "Google Books no está disponible por un problema de configuración del servicio."),
        UNAVAILABLE(503, "Google Books no está disponible temporalmente. Podés continuar con la carga manual."),
        TIMEOUT(503, "Google Books no respondió a tiempo. Podés continuar con la carga manual."),
        INVALID_RESPONSE(502, "Google Books devolvió una respuesta que no se pudo interpretar."),
        REQUEST_REJECTED(502, "Google Books no pudo procesar la consulta del servicio.");

        private final int status;
        private final String message;

        Failure(int status, String message) {
            this.status = status;
            this.message = message;
        }

        public int status() { return status; }
        public String code() { return "GOOGLE_BOOKS_" + name(); }
    }

    private final Failure failure;
    private final Integer upstreamStatus;
    private final String retryAfter;

    public GoogleBooksException(Failure failure, Integer upstreamStatus, String retryAfter) {
        super(failure.message);
        this.failure = failure;
        this.upstreamStatus = upstreamStatus;
        this.retryAfter = safeRetryAfter(retryAfter);
    }

    public Failure getFailure() { return failure; }
    public Integer getUpstreamStatus() { return upstreamStatus; }
    public String getRetryAfter() { return retryAfter; }

    private static String safeRetryAfter(String value) {
        if (value == null || value.length() > 80) return null;
        String trimmed = value.trim();
        if (trimmed.matches("[0-9]{1,10}")) {
            long seconds = Long.parseLong(trimmed);
            return seconds <= Integer.MAX_VALUE ? Long.toString(seconds) : null;
        }
        try {
            return ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .format(DateTimeFormatter.RFC_1123_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
