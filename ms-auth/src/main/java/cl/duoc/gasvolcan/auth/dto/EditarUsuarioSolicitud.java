package cl.duoc.gasvolcan.auth.dto;

import cl.duoc.gasvolcan.auth.model.Rol;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EditarUsuarioSolicitud(
		@NotBlank @Size(min = 2, max = 100) String nombre,
		@NotNull Rol rol,
		@Size(min = 8, max = 72) String password) {
}
