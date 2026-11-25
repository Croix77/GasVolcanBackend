package cl.duoc.gasvolcan.contacto.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record MensajeSolicitud(
        @NotBlank(message = "Ingresa tu nombre.")
        @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres.")
        @Pattern(regexp = "(?=[\\p{L}\\p{M}\\p{Zs}.'’-]*\\p{L})[\\p{L}\\p{M}\\p{Zs}.'’-]+",
                message = "El nombre debe contener letras y solo puede incluir espacios, puntos, apóstrofos y guiones.")
        String nombre,
        @NotBlank(message = "Escribe tu mensaje.")
        @Size(min = 10, max = 1000, message = "El mensaje debe tener entre 10 y 1000 caracteres.")
        String mensaje,
        @NotNull(message = "La referencia del mensaje es obligatoria.")
        UUID referencia) {

    private static final java.util.regex.Pattern EXTREMOS =
            java.util.regex.Pattern.compile("^[\\s\\p{Z}\\uFEFF]+|[\\s\\p{Z}\\uFEFF]+$");

    public MensajeSolicitud {
        nombre = limpiar(nombre);
        mensaje = limpiar(mensaje);
    }

    private static String limpiar(String valor) {
        return valor == null ? null : EXTREMOS.matcher(valor).replaceAll("");
    }
}
