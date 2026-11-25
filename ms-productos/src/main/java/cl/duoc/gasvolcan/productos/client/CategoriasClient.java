package cl.duoc.gasvolcan.productos.client;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CategoriasClient {

    private static final Logger log = LoggerFactory.getLogger(CategoriasClient.class);
    private static final String CATEGORIAS = "/api/v1/interno/categorias";
    private final RestClient rest;

    public CategoriasClient(
            @Value("${app.catalogo.categorias-url}") String url,
            @Value("${app.catalogo.token}") String token
    ) {
        if (token.length() < 32) {
            throw new IllegalStateException("Configura CATALOGO_INTERNO_TOKEN con al menos 32 caracteres.");
        }
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(4));
        this.rest = RestClient.builder().baseUrl(url)
                .requestFactory(factory)
                .defaultHeader("X-Catalogo-Key", token)
                .build();
    }

    public CategoriaVista[] listar() {
        try {
            var categorias = rest.get().uri(CATEGORIAS).retrieve().body(CategoriaVista[].class);
            if (categorias == null) {
                throw noDisponible();
            }
            return categorias;
        } catch (RestClientException error) {
            throw noDisponible();
        }
    }

    public Map<Long, String> nombres() {
        return Arrays.stream(listar()).collect(Collectors.toMap(CategoriaVista::id, CategoriaVista::nombre));
    }

    public CategoriaVista asociar(long categoriaId, String productoId, String referencia) {
        try {
            var categoria = rest.put().uri(CATEGORIAS + "/{id}/referencias/{ref}", categoriaId, referencia)
                    .body(Map.of("productoId", productoId))
                    .retrieve().body(CategoriaVista.class);
            if (categoria == null) {
                throw noDisponible();
            }
            return categoria;
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().value() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La categoría seleccionada no existe.");
            }
            throw noDisponible();
        } catch (RestClientException error) {
            throw noDisponible();
        }
    }

    public void liberar(String referencia) {
        try {
            rest.delete().uri(CATEGORIAS + "/referencias/{ref}", referencia).retrieve().toBodilessEntity();
        } catch (RestClientException error) {
            // Si no se confirma el retiro, la categoría queda protegida del borrado.
            log.warn("No se pudo retirar la referencia de categoría {}. Conserva el bloqueo hasta revisarla.", referencia);
        }
    }

    private ResponseStatusException noDisponible() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "El servicio de categorías no está disponible.");
    }

    public record CategoriaVista(long id, String nombre) {
    }
}
