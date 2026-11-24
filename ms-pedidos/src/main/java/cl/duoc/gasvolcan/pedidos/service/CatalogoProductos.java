package cl.duoc.gasvolcan.pedidos.service;

import cl.duoc.gasvolcan.pedidos.exception.CatalogoNoDisponibleException;
import cl.duoc.gasvolcan.pedidos.exception.RecursoNoEncontradoException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class CatalogoProductos {

    private final RestClient cliente;

    public CatalogoProductos(@Value("${app.catalogo.productos-url}") String url) {
        SimpleClientHttpRequestFactory transporte = new SimpleClientHttpRequestFactory();
        transporte.setConnectTimeout(Duration.ofSeconds(1));
        transporte.setReadTimeout(Duration.ofSeconds(2));
        cliente = RestClient.builder().baseUrl(url).requestFactory(transporte).build();
    }

    public ProductoCatalogo buscar(String id, String token) {
        ProductoCatalogo producto;
        try {
            producto = cliente.get()
                    .uri(uri -> uri.pathSegment("api", "v1", "productos", id, "precio").build())
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .body(ProductoCatalogo.class);
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().value() == 404) {
                throw new RecursoNoEncontradoException("Producto no disponible: " + id);
            }
            throw new CatalogoNoDisponibleException();
        } catch (RestClientException error) {
            throw new CatalogoNoDisponibleException();
        }
        if (producto == null || !id.equals(producto.id()) || producto.nombre() == null
                || producto.nombre().isBlank() || producto.precio() == null || producto.precio() < 0) {
            throw new CatalogoNoDisponibleException();
        }
        return producto;
    }

    public record ProductoCatalogo(String id, String nombre, Integer precio) {}
}
