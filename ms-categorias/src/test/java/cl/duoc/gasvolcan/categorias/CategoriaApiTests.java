package cl.duoc.gasvolcan.categorias;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:categorias_test;MODE=MySQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.catalogo.token=catalogo-pruebas-locales-0123456789-abcdef",
        "app.seed.catalogo=true"
})
class CategoriaApiTests {

    private static final String RUTA = "/api/v1/categorias";
    private static final String INTERNA = "/api/v1/interno/categorias";
    private static final String LLAVE = "catalogo-pruebas-locales-0123456789-abcdef";

    @LocalServerPort
    int puerto;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    private HttpRequest solicitud(String metodo, String ruta, String rol, String llave, String cuerpo) {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo));
        if (cuerpo != null) {
            builder.header("Content-Type", "application/json");
        }
        if (rol != null) {
            builder.header("Authorization", "Bearer " + token(rol));
        }
        if (llave != null) {
            builder.header("X-Catalogo-Key", llave);
        }
        return builder.build();
    }

    private JsonNode enviar(String metodo, String ruta, String rol, String llave, String cuerpo, int esperado)
            throws Exception {
        var respuesta = http.send(solicitud(metodo, ruta, rol, llave, cuerpo), HttpResponse.BodyHandlers.ofString());
        assertThat(respuesta.statusCode()).as(metodo + " " + ruta + " - " + respuesta.body()).isEqualTo(esperado);
        if (respuesta.body().isBlank()) {
            return json.readTree("null");
        }
        return json.readTree(respuesta.body());
    }

    private String token(String rol) {
        try {
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            String firma = encoder.encodeToString("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));
            var datos = Map.of("iss", "gasvolcan-auth", "sub", "prueba@example.test",
                    "rol", rol, "exp", Instant.now().plusSeconds(180).getEpochSecond());
            firma += "." + encoder.encodeToString(json.writeValueAsBytes(datos));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(new byte[32], "HmacSHA256"));
            return firma + "." + encoder.encodeToString(mac.doFinal(firma.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    @Test
    void permiteConsultarYRestringeLaEscrituraAAdministracion() throws Exception {
        enviar("GET", RUTA, null, null, null, 401);
        assertThat(enviar("GET", RUTA, "CLIENTE", null, null, 200).size()).isGreaterThanOrEqualTo(4);
        enviar("POST", RUTA, "CLIENTE", null, "{\"nombre\":\"Otra\"}", 403);
        enviar("POST", RUTA, "ADMIN", null, "{\"nombre\":\"  \"}", 400);
        long id = enviar("POST", RUTA, "ADMIN", null, "{\"nombre\":\"Accesorios del ensayo\"}", 201)
                .get("id").asLong();
        String ruta = RUTA + "/" + id;
        assertThat(enviar("PUT", ruta, "ADMIN", null, "{\"nombre\":\"Accesorios vigentes\"}", 200)
                .get("nombre").asText()).isEqualTo("Accesorios vigentes");
        enviar("DELETE", ruta, "ADMIN", null, null, 204);
        enviar("GET", ruta, "ADMIN", null, null, 404);
        assertThat(enviar("GET", "/v3/api-docs", null, null, null, 200).path("paths").has(RUTA)).isTrue();
    }

    @Test
    void laCategoriaSeProtegeSiAsociacionYBorradoLleganJuntos() throws Exception {
        long id = enviar("POST", RUTA, "ADMIN", null, "{\"nombre\":\"Categoría concurrente\"}", 201).get("id").asLong();
        String referencia = UUID.randomUUID().toString();
        String asociar = INTERNA + "/" + id + "/referencias/" + referencia;
        String cuerpo = "{\"productoId\":\"gas-5kg\"}";
        enviar("PUT", asociar, "ADMIN", null, cuerpo, 403);

        var alta = http.sendAsync(solicitud("PUT", asociar, null, LLAVE, cuerpo), HttpResponse.BodyHandlers.ofString());
        var baja = http.sendAsync(solicitud("DELETE", RUTA + "/" + id, "ADMIN", null, null), HttpResponse.BodyHandlers.ofString());
        int estadoAlta = alta.get().statusCode();
        int estadoBaja = baja.get().statusCode();

        if (estadoAlta == 200) {
            assertThat(estadoBaja).isEqualTo(409);
            enviar("PUT", asociar, null, LLAVE, cuerpo, 200);
            enviar("DELETE", INTERNA + "/referencias/" + referencia, null, LLAVE, null, 204);
            enviar("DELETE", RUTA + "/" + id, "ADMIN", null, null, 204);
        } else {
            assertThat(estadoAlta).isEqualTo(404);
            assertThat(estadoBaja).isEqualTo(204);
        }
    }
}
