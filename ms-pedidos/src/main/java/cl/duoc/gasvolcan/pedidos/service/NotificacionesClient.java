package cl.duoc.gasvolcan.pedidos.service;

import cl.duoc.gasvolcan.pedidos.dto.PedidoRespuesta;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class NotificacionesClient {

    private final RestClient destino;
    private final String clave;

    public NotificacionesClient(@Value("${app.notificaciones.url:}") String url,
            @Value("${app.notificaciones.token:}") String clave) {
        this.clave = clave == null ? "" : clave.trim();
        if (url == null || url.isBlank() || this.clave.isEmpty()) {
            this.destino = null;
            return;
        }
        SimpleClientHttpRequestFactory transporte = new SimpleClientHttpRequestFactory();
        transporte.setConnectTimeout(Duration.ofSeconds(1));
        transporte.setReadTimeout(Duration.ofSeconds(2));
        this.destino = RestClient.builder().baseUrl(url.trim()).requestFactory(transporte).build();
    }

    public void avisarPedido(PedidoRespuesta pedido) {
        if (destino == null) return;
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("canal", "EMAIL");
        cuerpo.put("destino", pedido.correoCliente());
        cuerpo.put("asunto", "Pedido confirmado en Gas El Volcán");
        cuerpo.put("mensaje", "Hola " + pedido.nombre() + ", tu pedido " + pedido.id()
                + " fue confirmado. Total: $" + pedido.total() + ".");
        cuerpo.put("referencia", "pedido-" + pedido.id());
        cuerpo.put("idempotencia", "pedido-" + pedido.id());
        try {
            destino.post()
                    .uri("/api/v1/interno/notificaciones")
                    .header("X-Notificaciones-Key", clave)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ignorado) {
        }
    }
}
