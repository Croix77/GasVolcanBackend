package cl.duoc.gasvolcan.productos;

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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:productos_test;MODE=MySQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.catalogo.token=catalogo-pruebas-locales-0123456789-abcdef",
        "app.seed.catalogo=true"
})
class ProductoApiTests {

    private static final String RUTA = "/api/v1/productos";
    private static final String LLAVE = "catalogo-pruebas-locales-0123456789-abcdef";
    private static final String LISTADO = """
            [{"id":1,"nombre":"Cilindros de Gas"},{"id":2,"nombre":"Reguladores"},
             {"id":3,"nombre":"Mangueras y Conexiones"},{"id":4,"nombre":"Accesorios"}]
            """;
    private static final Map<String, Long> REFERENCIAS = new ConcurrentHashMap<>();
    private static final HttpServer CATEGORIAS = servidorCategorias();

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

    @DynamicPropertySource
    static void direccionCategorias(DynamicPropertyRegistry registry) {
        registry.add("app.catalogo.categorias-url", () -> "http://127.0.0.1:" + CATEGORIAS.getAddress().getPort());
    }

    @AfterAll
    static void cerrarCategorias() {
        CATEGORIAS.stop(0);
    }

    @Test
    void conservaElCatalogoYPermiteConsultarPrecioSinStock() throws Exception {
        assertThat(enviar("GET", RUTA, null, null, null, 200).size()).isGreaterThanOrEqualTo(14);
        assertThat(enviar("GET", RUTA, "CLIENTE", null, null, 200).size()).isGreaterThanOrEqualTo(14);
        var gas = enviar("GET", RUTA + "/gas-5kg", "CLIENTE", null, null, 200);
        assertThat(gas.has("stock")).isFalse();
        assertThat(gas.get("nombre").asText()).isEqualTo("Cilindro de Gas 5 Kg");
        assertThat(gas.get("categoria").asText()).isEqualTo("Cilindros de Gas");
        assertThat(gas.get("precioAnterior").asInt()).isEqualTo(6500);
        assertThat(gas.get("imagen").asText()).isEqualTo("/img/gas-5kg.png");
        assertThat(enviar("GET", RUTA + "/gas-5kg/precio", "CLIENTE", null, null, 200).get("precio").asInt())
                .isEqualTo(5900);
        enviar("PUT", RUTA + "/gas-5kg/precio", "CLIENTE", null, "{\"precio\":0}", 403);
        assertThat(enviar("GET", "/v3/api-docs", null, null, null, 200).path("paths").has(RUTA)).isTrue();
    }

    @Test
    void respetaCategoriaVersionYBajaLogica() throws Exception {
        String datos = """
                {"codigo":"ficha-taller","nombre":"Ficha taller","descripcion":"Ficha temporal",
                 "unidad":"Unidad","precio":10,"categoriaId":88}
                """;
        enviar("POST", RUTA, "ADMIN", null, datos, 404);
        var nuevo = enviar("POST", RUTA, "ADMIN", null, datos.replace("88", "4"), 201);
        int version = nuevo.get("version").asInt();
        long antes = REFERENCIAS.values().stream().filter(id -> id == 4).count();
        String ruta = RUTA + "/ficha-taller";
        String cambios = """
                {"codigo":"ficha-taller","nombre":"Ficha actualizada","descripcion":"Ficha temporal",
                 "unidad":"Unidad","precio":20,"categoriaId":2,"version":%d}
                """.formatted(version);

        var actualizado = enviar("PUT", ruta, "ADMIN", null, cambios, 200);
        assertThat(actualizado.get("categoriaId").asInt()).isEqualTo(2);
        assertThat(actualizado.get("precio").asInt()).isEqualTo(20);
        assertThat(REFERENCIAS.values().stream().filter(id -> id == 4).count()).isEqualTo(antes - 1);
        enviar("PUT", ruta, "ADMIN", null, cambios, 409);
        enviar("DELETE", ruta, "ADMIN", null, null, 204);
        enviar("GET", ruta, "ADMIN", null, null, 404);
        enviar("POST", RUTA, "ADMIN", null, datos.replace("88", "4"), 409);
    }

    private static HttpServer servidorCategorias() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext("/api/v1/interno/categorias", ProductoApiTests::simularCategorias);
            servidor.start();
            return servidor;
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void simularCategorias(HttpExchange peticion) throws IOException {
        try (peticion) {
            if (!LLAVE.equals(peticion.getRequestHeaders().getFirst("X-Catalogo-Key"))) {
                peticion.getRequestBody().readAllBytes();
                peticion.sendResponseHeaders(401, -1);
                return;
            }
            peticion.getRequestBody().readAllBytes();
            String[] partes = peticion.getRequestURI().getPath().split("/");
            switch (peticion.getRequestMethod()) {
                case "GET" -> escribir(peticion, LISTADO);
                case "PUT" -> {
                    long id = Long.parseLong(partes[5]);
                    if (id < 1 || id > 4) {
                        peticion.sendResponseHeaders(404, -1);
                        return;
                    }
                    REFERENCIAS.put(partes[7], id);
                    String nombre = switch ((int) id) {
                        case 1 -> "Cilindros de Gas";
                        case 2 -> "Reguladores";
                        case 3 -> "Mangueras y Conexiones";
                        default -> "Accesorios";
                    };
                    escribir(peticion, "{\"id\":" + id + ",\"nombre\":\"" + nombre + "\"}");
                }
                case "DELETE" -> {
                    REFERENCIAS.remove(partes[6]);
                    peticion.sendResponseHeaders(204, -1);
                }
                default -> peticion.sendResponseHeaders(405, -1);
            }
        }
    }

    private static void escribir(HttpExchange peticion, String json) throws IOException {
        byte[] cuerpo = json.getBytes(StandardCharsets.UTF_8);
        peticion.getResponseHeaders().set("Content-Type", "application/json");
        peticion.sendResponseHeaders(200, cuerpo.length);
        peticion.getResponseBody().write(cuerpo);
    }
}
