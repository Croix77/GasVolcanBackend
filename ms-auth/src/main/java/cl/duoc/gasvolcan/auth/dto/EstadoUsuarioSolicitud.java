package cl.duoc.gasvolcan.auth.dto;

import jakarta.validation.constraints.NotNull;

public record EstadoUsuarioSolicitud(@NotNull Boolean activo) {
}
