package com.EduardoMango.Biblioteca.feature.book.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Cambio de estado del libro, sin eliminar datos ni modificar stock")
public record BookStatusUpdateRequest(
        @NotNull(message = "El estado activo es obligatorio")
        @Schema(description = "false para dar de baja, true para reactivar", example = "false")
        Boolean activo
) {
}
