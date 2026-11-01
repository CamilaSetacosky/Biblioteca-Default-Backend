package com.EduardoMango.Biblioteca.feature.author;

import java.time.LocalDate;
import java.util.Locale;

public final class AuthorIdentity {
    public static final String CONSTRAINT_NAME = "uk_author_identity";
    public static final String DUPLICATE_MESSAGE =
            "Ya existe un autor con el mismo nombre, apellido y fecha de nacimiento. Seleccioná el autor existente.";

    private AuthorIdentity() {
    }

    public static String cleanName(String value) {
        return value == null ? "" : value.replaceAll("(?U)\\s+", " ").trim();
    }

    public static String key(String firstName, String lastName, LocalDate birthDate) {
        String first = cleanName(firstName).toLowerCase(Locale.ROOT);
        String last = cleanName(lastName).toLowerCase(Locale.ROOT);
        String date = birthDate == null ? "unknown" : birthDate.toString();
        return first.length() + ":" + first + last.length() + ":" + last + ":" + date;
    }

    public static boolean isDuplicateConstraint(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().toLowerCase(Locale.ROOT).contains(CONSTRAINT_NAME)) {
                return true;
            }
        }
        return false;
    }
}
