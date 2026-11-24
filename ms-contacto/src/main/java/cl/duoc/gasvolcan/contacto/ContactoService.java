package cl.duoc.gasvolcan.contacto;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class ContactoService {

    private static final int MENSAJES_POR_PAGINA = 20;

    private final MensajeContactoRepository mensajes;

    ContactoService(MensajeContactoRepository mensajes) {
        this.mensajes = mensajes;
    }

    Envio recibir(MensajeSolicitud solicitud, String identidad) {
        String correo = identidad.toLowerCase(Locale.ROOT);
        String referencia = solicitud.referencia().toString();

        var anterior = mensajes.findByCorreoAndReferencia(correo, referencia);
        if (anterior.isPresent()) {
            return new Envio(anterior.get().recepcion(), false);
        }

        try {
            var guardado = mensajes.saveAndFlush(
                    new MensajeContacto(solicitud.nombre(), correo, solicitud.mensaje(), referencia));
            return new Envio(guardado.recepcion(), true);
        } catch (DataIntegrityViolationException error) {
            // La escritura del repositorio ya terminó. Si otro envío ganó la carrera, recuperamos su recepción.
            return mensajes.findByCorreoAndReferencia(correo, referencia)
                    .map(mensaje -> new Envio(mensaje.recepcion(), false))
                    .orElseThrow(() -> error);
        }
    }

    Bandeja listar(int pagina) {
        if (pagina < 0 || pagina > Integer.MAX_VALUE / MENSAJES_POR_PAGINA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La página está fuera de rango.");
        }
        var resultado = mensajes.findAllByOrderByFechaCreacionDescIdDesc(PageRequest.of(pagina, MENSAJES_POR_PAGINA));
        return new Bandeja(resultado.getContent().stream().map(MensajeContacto::datoBandeja).toList(),
                resultado.getNumber(), resultado.hasNext());
    }

    record Envio(Recepcion recepcion, boolean nuevo) {
    }

    record Recepcion(Long id, Instant fechaCreacion) {
    }

    record Mensaje(Long id, String nombre, String correo, String mensaje, Instant fechaCreacion) {
    }

    record Bandeja(List<Mensaje> mensajes, int pagina, boolean haySiguiente) {
    }
}
