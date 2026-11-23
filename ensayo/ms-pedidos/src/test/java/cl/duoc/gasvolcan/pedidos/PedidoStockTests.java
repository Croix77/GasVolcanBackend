package cl.duoc.gasvolcan.pedidos;

import static org.assertj.core.api.Assertions.assertThat;

import cl.duoc.gasvolcan.pedidos.model.Existencia;
import cl.duoc.gasvolcan.pedidos.repository.ExistenciaRepository;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:pedidos_ensayo;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
class PedidoStockTests {

    private static final String PEDIDOS = "/api/v1/pedidos";
    private static final AtomicInteger PRECIO = new AtomicInteger(16000);
    private static final AtomicInteger LLAMADAS = new AtomicInteger();
    private static final HttpServer CATALOGO = catalogo();

    @LocalServerPort
    int puerto;
    @Autowired
    ExistenciaRepository existencias;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @DynamicPropertySource
    static void urlCatalogo(DynamicPropertyRegistry registro) {
        registro.add("app.catalogo.productos-url", () -> "http://127.0.0.1:" + CATALOGO.getAddress().getPort());
    }

    @BeforeEach
    void prepararStockYPrecio() {
        existencias.save(new Existencia("gas-5kg", 1));
        existencias.save(new Existencia("gas-15kg", 4));
        PRECIO.set(16000);
    }

    @AfterAll
    static void cerrarCatalogoDePrueba() {
        CATALOGO.stop(0);
    }

    @Test
    void soloUnClientePuedeComprarLaUltimaUnidad() throws Exception {
        var uno = http.sendAsync(solicitud("POST", PEDIDOS, "cliente1@example.test",
                datos("[{\"id\":\"gas-5kg\",\"cantidad\":1}]", UUID.randomUUID())), HttpResponse.BodyHandlers.ofString());
        var dos = http.sendAsync(solicitud("POST", PEDIDOS, "cliente2@example.test",
                datos("[{\"id\":\"gas-5kg\",\"cantidad\":1}]", UUID.randomUUID())), HttpResponse.BodyHandlers.ofString());
        CompletableFuture.allOf(uno, dos).get();

        assertThat(new int[] {uno.get().statusCode(), dos.get().statusCode()}).containsExactlyInAnyOrder(201, 409);
        assertThat(existencias.findById("gas-5kg").orElseThrow().getStock()).isZero();
    }

    @Test
    void revierteElPrimerDescuentoSiElSegundoProductoNoTieneStock() throws Exception {
        existencias.save(new Existencia("gas-5kg", 0));
        String datos = datos("""
            [{"id":"gas-15kg","cantidad":1},{"id":"gas-5kg","cantidad":1}]
            """, UUID.randomUUID());
        enviar("POST", PEDIDOS, "carrito@example.test", datos, 409);
        assertThat(existencias.findById("gas-15kg").orElseThrow().getStock()).isEqualTo(4);
        assertThat(enviar("GET", PEDIDOS, "carrito@example.test", null, 200).size()).isZero();
    }

    @Test
    void proteccionDePrecioYReintentoSinCatalogo() throws Exception {
        UUID referencia = UUID.randomUUID();
        String correo = "precio@example.test";
        PRECIO.set(17000);
        String desactualizado = datos("[{\"id\":\"gas-15kg\",\"cantidad\":1,\"precioEsperado\":16000}]", referencia);
        enviar("POST", PEDIDOS, correo, desactualizado, 409);
        assertThat(existencias.findById("gas-15kg").orElseThrow().getStock()).isEqualTo(4);

        String actual = desactualizado.replace("16000", "17000");
        JsonNode pedido = enviar("POST", PEDIDOS, correo, actual, 201);
        assertThat(pedido.path("total").asLong()).isEqualTo(17000);
        assertThat(existencias.findById("gas-15kg").orElseThrow().getStock()).isEqualTo(3);

        PRECIO.set(-1);
        int antes = LLAMADAS.get();
        JsonNode repetido = enviar("POST", PEDIDOS, correo, actual, 200);
        assertThat(repetido.path("id").asLong()).isEqualTo(pedido.path("id").asLong());
        assertThat(repetido.path("items").size()).isEqualTo(1);
        assertThat(LLAMADAS.get()).isEqualTo(antes);
        enviar("POST", PEDIDOS, correo, actual.replace(referencia.toString(), UUID.randomUUID().toString()), 503);
        assertThat(existencias.findById("gas-15kg").orElseThrow().getStock()).isEqualTo(3);
        assertThat(enviar("GET", PEDIDOS, correo, null, 200).size()).isEqualTo(1);
    }

    private static HttpServer catalogo() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext("/api/v1/productos/", intercambio -> {
                LLAMADAS.incrementAndGet();
                String autorizacion = intercambio.getRequestHeaders().getFirst("Authorization");
                boolean cinco = intercambio.getRequestURI().getPath().endsWith("gas-5kg");
                int precio = cinco ? 5900 : PRECIO.get();
                int respuesta = autorizacion == null || !autorizacion.startsWith("Bearer ") ? 401 : precio < 0 ? 503 : 200;
                if (respuesta == 200) {
                    String id = cinco ? "gas-5kg" : "gas-15kg";
                    byte[] cuerpo = ("{\"id\":\"" + id + "\",\"nombre\":\"Cilindro\",\"precio\":" + precio + "}")
                            .getBytes(StandardCharsets.UTF_8);
                    intercambio.getResponseHeaders().set("Content-Type", "application/json");
                    intercambio.sendResponseHeaders(respuesta, cuerpo.length);
                    intercambio.getResponseBody().write(cuerpo);
                } else {
                    intercambio.sendResponseHeaders(respuesta, -1);
                }
                intercambio.close();
            });
            servidor.start();
            return servidor;
        } catch (IOException error) {
            throw new IllegalStateException("No se pudo iniciar el catálogo de prueba.", error);
        }
    }

    private String datos(String items, UUID referencia) {
        return """
            {"nombre":"Cliente Prueba","telefono":"+56 9 1234 5678",
             "direccion":"Calle de prueba 123","comuna":"Chillán","items":%s,
             "latitudEntrega":-36.61,"longitudEntrega":-72.10,"referenciaCompra":"%s"}
            """.formatted(items, referencia);
    }

    private JsonNode enviar(String metodo, String ruta, String correo, String datos, int esperado) throws Exception {
        var respuesta = http.send(solicitud(metodo, ruta, correo, datos), HttpResponse.BodyHandlers.ofString());
        assertThat(respuesta.statusCode()).as("%s %s %s", metodo, ruta, respuesta.body()).isEqualTo(esperado);
        return respuesta.body().isBlank() ? json.createObjectNode() : json.readTree(respuesta.body());
    }

    private HttpRequest solicitud(String metodo, String ruta, String correo, String datos) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .method(metodo, datos == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(datos))
                .header("Authorization", "Bearer " + token(correo));
        if (datos != null) request.header("Content-Type", "application/json");
        return request.build();
    }

    private String token(String correo) throws Exception {
        var base64 = Base64.getUrlEncoder().withoutPadding();
        long ahora = Instant.now().getEpochSecond();
        String cabecera = "{\"typ\":\"JWT\",\"alg\":\"HS256\"}";
        String cuerpo = """
            {"iss":"gasvolcan-auth","sub":"%s","rol":"CLIENTE","iat":%d,"exp":%d}
            """.formatted(correo, ahora - 120, ahora + 3600);
        String datos = base64.encodeToString(cabecera.getBytes(StandardCharsets.UTF_8)) + "."
                + base64.encodeToString(cuerpo.getBytes(StandardCharsets.UTF_8));
        Mac firma = Mac.getInstance("HmacSHA256");
        firma.init(new SecretKeySpec(new byte[32], "HmacSHA256"));
        return datos + "." + base64.encodeToString(firma.doFinal(datos.getBytes(StandardCharsets.UTF_8)));
    }
}
