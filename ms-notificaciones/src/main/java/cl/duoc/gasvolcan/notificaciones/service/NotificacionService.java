package cl.duoc.gasvolcan.notificaciones.service;

import cl.duoc.gasvolcan.notificaciones.model.Canal;
import cl.duoc.gasvolcan.notificaciones.model.Notificacion;
import cl.duoc.gasvolcan.notificaciones.repository.NotificacionRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class NotificacionService {

    private final NotificacionRepository notificaciones;
    private final RestClient webhook;

    public NotificacionService(NotificacionRepository notificaciones,
            @Value("${app.notificaciones.webhook-url:}") String url) {
        this.notificaciones = notificaciones;
        if (url == null || url.isBlank()) {
            this.webhook = null;
            return;
        }
        SimpleClientHttpRequestFactory transporte = new SimpleClientHttpRequestFactory();
        transporte.setConnectTimeout(Duration.ofSeconds(1));
        transporte.setReadTimeout(Duration.ofSeconds(2));
        this.webhook = RestClient.builder().baseUrl(url.trim()).requestFactory(transporte).build();
    }

    public Resultado enviar(Solicitud solicitud) {
        if (solicitud.canal() == Canal.EMAIL && !solicitud.destino().contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El destino EMAIL debe ser un correo válido.");
        }
        String clave = solicitud.idempotencia() == null || solicitud.idempotencia().isBlank()
                ? null : solicitud.idempotencia().trim();
        if (clave != null) {
            var previa = notificaciones.findByIdempotencia(clave);
            if (previa.isPresent()) {
                return new Resultado(previa.get().vista(), false);
            }
        }
        Notificacion nota;
        try {
            nota = notificaciones.save(new Notificacion(solicitud.canal(), solicitud.destino().trim(),
                    solicitud.asunto(), solicitud.mensaje().trim(), solicitud.referencia(), clave));
        } catch (DataIntegrityViolationException conflicto) {
            if (clave == null) throw conflicto;
            return new Resultado(notificaciones.findByIdempotencia(clave)
                    .orElseThrow(() -> conflicto).vista(), false);
        }
        if (webhook == null) {
            nota.simulada("Entrega simulada. Configura NOTIFICACIONES_WEBHOOK_URL para envío real.");
        } else {
            entregar(nota);
        }
        return new Resultado(notificaciones.save(nota).vista(), true);
    }

    public Notificacion.Vista buscar(long id) {
        return notificaciones.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notificación no encontrada."))
                .vista();
    }

    public List<Notificacion.Vista> listar() {
        return notificaciones.findTop100ByOrderByIdDesc().stream().map(Notificacion::vista).toList();
    }

    private void entregar(Notificacion nota) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("canal", nota.getCanal().name());
        cuerpo.put("destino", nota.getDestino());
        cuerpo.put("mensaje", nota.getMensaje());
        if (nota.getAsunto() != null) {
            cuerpo.put("asunto", nota.getAsunto());
        }
        if (nota.getReferencia() != null) {
            cuerpo.put("referencia", nota.getReferencia());
        }
        if (nota.getIdempotencia() != null) {
            cuerpo.put("idempotencia", nota.getIdempotencia());
        }
        try {
            webhook.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .toBodilessEntity();
            nota.enviada("WEBHOOK", "Entrega aceptada por el proveedor.");
        } catch (RestClientException error) {
            String causa = error.getMessage() == null ? "No se pudo contactar al proveedor." : error.getMessage();
            nota.fallida(causa);
        }
    }

    public record Resultado(Notificacion.Vista vista, boolean creado) {
    }

    public record Solicitud(
            @NotNull(message = "El canal es obligatorio.") Canal canal,
            @NotBlank(message = "El destino es obligatorio.") @Size(max = 254, message = "El destino admite hasta 254 caracteres.") String destino,
            @Size(max = 120, message = "El asunto admite hasta 120 caracteres.") String asunto,
            @NotBlank(message = "El mensaje es obligatorio.") @Size(max = 2000, message = "El mensaje admite hasta 2000 caracteres.") String mensaje,
            @Size(max = 100, message = "La referencia admite hasta 100 caracteres.") String referencia,
            @Size(max = 120, message = "La idempotencia admite hasta 120 caracteres.") String idempotencia
    ) {
    }
}
