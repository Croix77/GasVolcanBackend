package cl.duoc.gasvolcan.auth.dto;

import cl.duoc.gasvolcan.auth.model.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CrearUsuarioSolicitud(
		@NotBlank @Size(min = 2, max = 100) String nombre,
		@NotBlank @Email @Size(max = 150) String correo,
		@NotNull Rol rol,
		@NotBlank @Size(min = 8, max = 72) String password) {
}
