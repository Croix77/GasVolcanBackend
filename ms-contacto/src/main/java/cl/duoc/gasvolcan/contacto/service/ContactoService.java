package cl.duoc.gasvolcan.contacto.service;

import cl.duoc.gasvolcan.contacto.dto.MensajeSolicitud;
import cl.duoc.gasvolcan.contacto.model.MensajeContacto;
import cl.duoc.gasvolcan.contacto.repository.MensajeContactoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ContactoService {

    private static final int MENSAJES_POR_PAGINA = 20;

    private final MensajeContactoRepository mensajes;

    public ContactoService(MensajeContactoRepository mensajes) {
        this.mensajes = mensajes;
    }

    public Envio recibir(MensajeSolicitud solicitud, String identidad) {
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

    public Bandeja listar(int pagina) {
        if (pagina < 0 || pagina > Integer.MAX_VALUE / MENSAJES_POR_PAGINA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La página está fuera de rango.");
        }
        var resultado = mensajes.findAllByOrderByFechaCreacionDescIdDesc(PageRequest.of(pagina, MENSAJES_POR_PAGINA));
        return new Bandeja(resultado.getContent().stream().map(MensajeContacto::datoBandeja).toList(),
                resultado.getNumber(), resultado.hasNext());
    }

    public record Envio(Recepcion recepcion, boolean nuevo) {
    }

    public record Recepcion(Long id, Instant fechaCreacion) {
    }

    public record Mensaje(Long id, String nombre, String correo, String mensaje, Instant fechaCreacion) {
    }

    public record Bandeja(List<Mensaje> mensajes, int pagina, boolean haySiguiente) {
    }
}
