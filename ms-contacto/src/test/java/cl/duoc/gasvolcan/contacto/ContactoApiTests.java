package cl.duoc.gasvolcan.contacto;

import cl.duoc.gasvolcan.contacto.model.MensajeContacto;
import cl.duoc.gasvolcan.contacto.repository.MensajeContactoRepository;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:contacto_test;MODE=MySQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
class ContactoApiTests {

    private static final String RUTA = "/api/v1/contacto/mensajes";
    private static final String CORREO = "cliente@example.test";
    private static final String ADMIN = "administrador@example.test";

    @LocalServerPort
    private int puerto;

    @Autowired
    private MensajeContactoRepository mensajes;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void limpiarBandeja() {
        mensajes.deleteAllInBatch();
    }

    @Test
    void protegeBandejaYEscrituraSegunElRol() throws Exception {
        String cuerpo = cuerpo("María Pérez", "Consulta sobre horario.", UUID.randomUUID());

        assertThat(enviar("POST", RUTA, null, cuerpo, 401).has("mensaje")).isTrue();
        enviar("GET", RUTA, null, null, 401);
        enviar("GET", RUTA, token("CLIENTE", CORREO), null, 403);
        enviar("GET", RUTA, token("OPERADORA", CORREO), null, 403);
        enviar("POST", RUTA, token("OPERADORA", CORREO), cuerpo, 403);
        enviar("POST", RUTA, token("VENDEDOR", CORREO), cuerpo, 403);
        enviar("POST", RUTA, token("REPARTIDOR", CORREO), cuerpo, 403);
        enviar("POST", RUTA, token("ADMIN", ADMIN), cuerpo, 201);
        assertThat(enviar("GET", RUTA, token("ADMIN", ADMIN), null, 200).path("mensajes").size()).isEqualTo(1);
    }

    @Test
    void validaDatosLimpiosYRechazaIdentidadEnElCuerpo() throws Exception {
        String cliente = token("CLIENTE", CORREO);
        UUID referencia = UUID.randomUUID();

        JsonNode respuesta = enviar("POST", RUTA, cliente, cuerpo("  ", "  ocho  ", referencia), 400);
        assertThat(respuesta.path("campos").has("nombre")).isTrue();
        assertThat(respuesta.path("campos").has("mensaje")).isTrue();
        respuesta = enviar("POST", RUTA, cliente, cuerpo("Ana1", "a".repeat(1001), referencia), 400);
        assertThat(respuesta.path("campos").has("nombre")).isTrue();
        assertThat(respuesta.path("campos").has("mensaje")).isTrue();

        String conCorreo = json.writeValueAsString(Map.of("nombre", "María", "mensaje", "Consulta válida.",
                "referencia", referencia, "correo", "ajeno@example.test"));
        assertThat(enviar("POST", RUTA, cliente, conCorreo, 400).has("mensaje")).isTrue();
        String sinUuid = json.writeValueAsString(Map.of("nombre", "María", "mensaje", "Consulta válida."));
        assertThat(enviar("POST", RUTA, cliente, sinUuid, 400).path("campos").has("referencia")).isTrue();
        String uuidInvalido = json.writeValueAsString(Map.of("nombre", "María", "mensaje", "Consulta válida.",
                "referencia", "invalida"));
        enviar("POST", RUTA, cliente, uuidInvalido, 400);
        assertThat(mensajes.count()).isZero();
    }

    @Test
    void confirmaElMismoMensajeCuandoLosReintentosLleganJuntos() throws Exception {
        UUID referencia = UUID.randomUUID();
        String cuerpo = cuerpo("  María-José O’Neill   ", "  Quisiera consultar.\n¿Hay reparto mañana?  ", referencia);
        String sesion = token("CLIENTE", "CLIENTE@example.test");

        var primerEnvio = http.sendAsync(solicitud("POST", RUTA, sesion, cuerpo), HttpResponse.BodyHandlers.ofString());
        var segundoEnvio = http.sendAsync(solicitud("POST", RUTA, sesion, cuerpo), HttpResponse.BodyHandlers.ofString());
        var primeraRespuesta = primerEnvio.get();
        var segundaRespuesta = segundoEnvio.get();

        assertThat(new int[]{primeraRespuesta.statusCode(), segundaRespuesta.statusCode()})
                .containsExactlyInAnyOrder(201, 200);
        JsonNode recepcion = json.readTree(primeraRespuesta.body());
        assertThat(recepcion).isEqualTo(json.readTree(segundaRespuesta.body()));
        assertThat(recepcion.has("id")).isTrue();
        assertThat(recepcion.has("fechaCreacion")).isTrue();
        assertThat(recepcion.has("correo")).isFalse();
        assertThat(enviar("POST", RUTA, sesion, cuerpo, 200)).isEqualTo(recepcion);
        assertThat(mensajes.count()).isEqualTo(1);

        var bandeja = enviar("GET", RUTA, token("ADMIN", ADMIN), null, 200);
        JsonNode mensaje = bandeja.path("mensajes").get(0);
        assertThat(mensaje.path("id")).isEqualTo(recepcion.path("id"));
        assertThat(mensaje.path("correo").asText()).isEqualTo(CORREO);
        assertThat(mensaje.path("nombre").asText()).isEqualTo("María-José O’Neill");
        assertThat(mensaje.path("mensaje").asText()).isEqualTo("Quisiera consultar.\n¿Hay reparto mañana?");

        enviar("POST", RUTA, token("CLIENTE", "otra@example.test"), cuerpo, 201);
        assertThat(mensajes.count()).isEqualTo(2);
    }

    @Test
    void paginaLosResultadosRecientesSinDuplicarlos() throws Exception {
        for (int numero = 1; numero <= 21; numero++) {
            mensajes.save(new MensajeContacto("María", CORREO,
                    "Consulta número " + numero, UUID.randomUUID().toString()));
        }
        String sesionAdmin = token("ADMIN", ADMIN);
        var primera = enviar("GET", RUTA + "?pagina=0", sesionAdmin, null, 200);
        assertThat(primera.path("pagina").asInt()).isZero();
        assertThat(primera.path("haySiguiente").asBoolean()).isTrue();
        assertThat(primera.path("mensajes").size()).isEqualTo(20);
        assertThat(primera.path("mensajes").get(0).path("mensaje").asText()).isEqualTo("Consulta número 21");

        var segunda = enviar("GET", RUTA + "?pagina=1", sesionAdmin, null, 200);
        assertThat(segunda.path("pagina").asInt()).isOne();
        assertThat(segunda.path("haySiguiente").asBoolean()).isFalse();
        assertThat(segunda.path("mensajes").size()).isEqualTo(1);
        assertThat(segunda.path("mensajes").get(0).path("mensaje").asText()).isEqualTo("Consulta número 1");
        assertThat(enviar("GET", RUTA + "?pagina=-1", sesionAdmin, null, 400).has("mensaje")).isTrue();
        enviar("GET", RUTA + "?pagina=no-numero", sesionAdmin, null, 400);
        enviar("GET", RUTA + "?pagina=2147483647", sesionAdmin, null, 400);
    }

    @Test
    void rechazaTokensConOtroEmisorOCalve() throws Exception {
        String cuerpo = cuerpo("María", "Consulta sobre horario.", UUID.randomUUID());
        enviar("POST", RUTA, token("CLIENTE", CORREO, "otro-emisor", new byte[32]), cuerpo, 401);
        byte[] otraClave = new byte[32];
        otraClave[0] = 1;
        enviar("POST", RUTA, token("CLIENTE", CORREO, "gasvolcan-auth", otraClave), cuerpo, 401);
    }

    @Test
    void documentaLaApiConBearerEnSwagger() throws Exception {
        var api = enviar("GET", "/v3/api-docs", null, null, 200);
        assertThat(api.path("paths").path(RUTA).has("post")).isTrue();
        assertThat(api.path("paths").path(RUTA).has("get")).isTrue();
        assertThat(api.path("components").path("securitySchemes").has("bearerAuth")).isTrue();
    }

    private String cuerpo(String nombre, String mensaje, UUID referencia) {
        return json.writeValueAsString(Map.of("nombre", nombre, "mensaje", mensaje, "referencia", referencia));
    }

    private HttpRequest solicitud(String metodo, String ruta, String token, String cuerpo) {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta))
                .timeout(Duration.ofSeconds(10))
                .method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(cuerpo));
        if (cuerpo != null) {
            builder.header("Content-Type", "application/json");
        }
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder.build();
    }

    private JsonNode enviar(String metodo, String ruta, String token, String cuerpo, int esperado) throws Exception {
        var respuesta = http.send(solicitud(metodo, ruta, token, cuerpo), HttpResponse.BodyHandlers.ofString());
        assertThat(respuesta.statusCode()).as("%s %s", metodo, ruta).isEqualTo(esperado);
        return json.readTree(respuesta.body());
    }

    private String token(String rol, String correo) throws Exception {
        return token(rol, correo, "gasvolcan-auth", new byte[32]);
    }

    private String token(String rol, String correo, String emisor, byte[] clave) throws Exception {
        var base64 = Base64.getUrlEncoder().withoutPadding();
        String cabecera = base64.encodeToString("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = base64.encodeToString(json.writeValueAsBytes(Map.of("iss", emisor,
                "sub", correo, "rol", rol, "exp", Instant.now().plusSeconds(180).getEpochSecond())));
        String entrada = cabecera + "." + claims;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(clave, "HmacSHA256"));
        return entrada + "." + base64.encodeToString(mac.doFinal(entrada.getBytes(StandardCharsets.UTF_8)));
    }
}
