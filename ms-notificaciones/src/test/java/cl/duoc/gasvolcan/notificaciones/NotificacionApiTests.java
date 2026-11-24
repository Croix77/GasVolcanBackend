package cl.duoc.gasvolcan.notificaciones;

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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:notificaciones_test;MODE=MySQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.notificaciones.token=notificaciones-pruebas-locales-0123456789-abcdef"
})
class NotificacionApiTests {

    private static final String RUTA = "/api/v1/notificaciones";
    private static final String INTERNA = "/api/v1/interno/notificaciones";
    private static final String LLAVE = "notificaciones-pruebas-locales-0123456789-abcdef";

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
            builder.header("X-Notificaciones-Key", llave);
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
    void separaLaRutaInternaDelAccesoPorRol() throws Exception {
        String cuerpo = """
                {"canal":"EMAIL","destino":"cliente@example.test","mensaje":"Tu pedido está en camino."}
                """;
        enviar("POST", INTERNA, null, null, cuerpo, 403);
        enviar("POST", INTERNA, null, "clave-errónea-0123456789-abcdefghijklmnop", cuerpo, 403);
        long id = enviar("POST", INTERNA, null, LLAVE, cuerpo, 201).get("id").asLong();

        enviar("GET", RUTA, null, null, null, 401);
        enviar("GET", RUTA, "CLIENTE", null, null, 403);
        assertThat(enviar("GET", RUTA, "ADMIN", null, null, 200).size()).isGreaterThanOrEqualTo(1);
        assertThat(enviar("GET", RUTA + "/" + id, "OPERADORA", null, null, 200).get("estado").asText())
                .isEqualTo("SIMULADO");
        assertThat(enviar("GET", "/v3/api-docs", null, null, null, 200).path("paths").has(RUTA)).isTrue();
    }

    @Test
    void validaElDestinoYRegistraLaEntregaSimulada() throws Exception {
        enviar("POST", INTERNA, null, LLAVE,
                "{\"canal\":\"EMAIL\",\"destino\":\"sin-arroba\",\"mensaje\":\"Hola\"}", 400);
        enviar("POST", INTERNA, null, LLAVE,
                "{\"canal\":\"WHATSAPP\",\"destino\":\"912345678\"}", 400);
        JsonNode creada = enviar("POST", INTERNA, null, LLAVE, """
                {"canal":"WHATSAPP","destino":"912345678","asunto":"Entrega","mensaje":"El repartidor llegó.",
                 "referencia":"pedido-42"}
                """, 201);
        assertThat(creada.get("canal").asText()).isEqualTo("WHATSAPP");
        assertThat(creada.get("estado").asText()).isEqualTo("SIMULADO");
        assertThat(creada.get("proveedor").asText()).isEqualTo("SIMULADO");
        assertThat(creada.get("simulado").asBoolean()).isTrue();
        assertThat(creada.get("referencia").asText()).isEqualTo("pedido-42");
        assertThat(creada.get("fechaEnvio").isNull()).isFalse();

        enviar("GET", RUTA + "/999999", "ADMIN", null, null, 404);
        enviar("GET", RUTA + "/" + creada.get("id").asLong(), "ADMIN", null, null, 200);
    }

    @Test
    void noDuplicaUnAvisoConLaMismaIdempotencia() throws Exception {
        String cuerpo = """
                {"canal":"EMAIL","destino":"cliente@example.test","mensaje":"Pedido confirmado.",
                 "referencia":"pedido-77","idempotencia":"pedido-77"}
                """;
        JsonNode primera = enviar("POST", INTERNA, null, LLAVE, cuerpo, 201);
        JsonNode repetida = enviar("POST", INTERNA, null, LLAVE, cuerpo, 200);

        assertThat(repetida.get("id").asLong()).isEqualTo(primera.get("id").asLong());
        assertThat(repetida.get("idempotencia").asText()).isEqualTo("pedido-77");
    }
}
