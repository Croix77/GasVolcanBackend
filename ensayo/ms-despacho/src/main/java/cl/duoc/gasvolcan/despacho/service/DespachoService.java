package cl.duoc.gasvolcan.despacho.service;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DespachoService {
    private final RestClient pedidos;
    private final RestClient auth;

    public DespachoService(
            @Value("${app.servicios.pedidos}") String pedidosUrl,
            @Value("${app.servicios.auth}") String authUrl) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(3));
        pedidos = RestClient.builder().baseUrl(pedidosUrl).requestFactory(factory).build();
        auth = RestClient.builder().baseUrl(authUrl).requestFactory(factory).build();
    }

    public ResponseEntity<byte[]> pedido(HttpMethod metodo, String sufijo, Object cuerpo, Jwt usuario) {
        try {
            String recurso = metodo == HttpMethod.GET ? "/api/v1/pedidos" : "/api/v1/interno/pedidos";
            RestClient.RequestBodySpec solicitud = pedidos.method(metodo)
                    .uri(recurso + sufijo)
                    // Pedidos consulta el rol y el correo del solicitante antes de aplicar el cambio.
                    .headers(cabeceras -> cabeceras.setBearerAuth(usuario.getTokenValue()))
                    .accept(MediaType.APPLICATION_JSON);
            if (cuerpo != null) {
                solicitud.contentType(MediaType.APPLICATION_JSON).body(cuerpo);
            }
            ResponseEntity<byte[]> respuesta = solicitud.retrieve().toEntity(byte[].class);
            return ResponseEntity.status(respuesta.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(respuesta.getBody());
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().is4xxClientError()) {
                return ResponseEntity.status(error.getStatusCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(error.getResponseBodyAsByteArray());
            }
            throw servicioNoDisponible("Pedidos");
        } catch (RestClientException error) {
            throw servicioNoDisponible("Pedidos");
        }
    }

    public void validarRepartidor(String correo, Jwt usuario) {
        Repartidor[] repartidores;
        try {
            repartidores = auth.get().uri("/api/auth/repartidores")
                    .headers(cabeceras -> cabeceras.setBearerAuth(usuario.getTokenValue()))
                    .retrieve().body(Repartidor[].class);
        } catch (RestClientException error) {
            throw servicioNoDisponible("Auth");
        }

        if (repartidores == null || Arrays.stream(repartidores)
                .noneMatch(repartidor -> repartidor.activo() && correo.equalsIgnoreCase(repartidor.correo()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El repartidor debe tener una cuenta activa.");
        }
    }

    public record Repartidor(String correo, boolean activo) {}

    private ResponseStatusException servicioNoDisponible(String nombre) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "El servicio de " + nombre + " no está disponible. Vuelve a intentar.");
    }
}
