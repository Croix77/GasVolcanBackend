package cl.duoc.gasvolcan.negocio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// El cliente solo envía el nombre. El identificador lo genera MySQL.
public record CategoriaSolicitud(
        @NotBlank(message = "Escribe el nombre de la categoría.")
        @Size(max = 80, message = "El nombre no puede superar los 80 caracteres.")
        String nombre
) {
    public CategoriaSolicitud {
        if (nombre != null) {
            nombre = nombre.strip();
        }
    }
}
