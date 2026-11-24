package cl.duoc.gasvolcan.despacho;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
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
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class DespachoTests {

    private static final String ESTADO = "/api/v1/despacho/1/estado";
    private static final String ASIGNACION = "/api/v1/despacho/1/asignacion";
    private static final AtomicInteger ESTADO_PERFIL = new AtomicInteger();
    private static final AtomicInteger LLAMADAS_PEDIDOS = new AtomicInteger();
    private static final HttpServer SERVICIOS = simularServicios();

    @LocalServerPort
    int puerto;

    private final HttpClient http = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void servicios(DynamicPropertyRegistry propiedades) {
        String base = "http://127.0.0.1:" + SERVICIOS.getAddress().getPort();
        propiedades.add("app.servicios.pedidos", () -> base);
        propiedades.add("app.servicios.auth", () -> base);
    }

    @BeforeEach
    void restablecerServicios() {
        ESTADO_PERFIL.set(200);
        LLAMADAS_PEDIDOS.set(0);
    }

    @AfterAll
    static void detenerServicios() {
        SERVICIOS.stop(0);
    }

    @Test
    void noActualizaUnPedidoConSesionRevocada() throws Exception {
        ESTADO_PERFIL.set(401);
        var respuesta = escribir(ESTADO, "REPARTIDOR", "{\"estado\":\"ENTREGADO\"}");
        assertThat(respuesta.statusCode()).isEqualTo(401);
        assertThat(LLAMADAS_PEDIDOS.get()).isZero();
    }

    @Test
    void actualizaUnPedidoConSesionActiva() throws Exception {
        var respuesta = escribir(ESTADO, "REPARTIDOR", "{\"estado\":\"ENTREGADO\"}");
        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"estado\":\"ENTREGADO\"");
        assertThat(LLAMADAS_PEDIDOS.get()).isEqualTo(1);
    }

    @Test
    void noAsignaUnRepartidorQueNoEsteActivo() throws Exception {
        var inactivo = escribir(ASIGNACION, "ADMIN", "{\"correoRepartidor\":\"inactivo@example.test\"}");
        assertThat(inactivo.statusCode()).isEqualTo(409);
        assertThat(LLAMADAS_PEDIDOS.get()).isZero();

        var activo = escribir(ASIGNACION, "ADMIN", "{\"correoRepartidor\":\"activo@example.test\"}");
        assertThat(activo.statusCode()).isEqualTo(200);
        assertThat(activo.body()).contains("\"estado\":\"ASIGNADO\"");
        assertThat(LLAMADAS_PEDIDOS.get()).isEqualTo(1);
    }

    private HttpResponse<String> escribir(String ruta, String rol, String datos) throws Exception {
        var solicitud = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta))
                .method("PATCH", HttpRequest.BodyPublishers.ofString(datos))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token(rol))
                .build();
        return http.send(solicitud, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpServer simularServicios() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext("/api/auth/me", intercambio ->
                    enviar(intercambio, ESTADO_PERFIL.get(), "{\"correo\":\"sesion@example.test\",\"activo\":true}"));
            servidor.createContext("/api/auth/repartidores", intercambio ->
                    enviar(intercambio, 200, "[{\"correo\":\"activo@example.test\",\"activo\":true}]"));
            servidor.createContext("/api/v1/interno/pedidos/", intercambio -> {
                LLAMADAS_PEDIDOS.incrementAndGet();
                boolean asignacion = intercambio.getRequestURI().getPath().endsWith("/asignacion");
                enviar(intercambio, 200, asignacion ? "{\"estado\":\"ASIGNADO\"}" : "{\"estado\":\"ENTREGADO\"}");
            });
            servidor.start();
            return servidor;
        } catch (IOException error) {
            throw new IllegalStateException("No fue posible iniciar los servicios de prueba.", error);
        }
    }

    private static void enviar(HttpExchange intercambio, int estado, String cuerpo) throws IOException {
        try (intercambio) {
            String autorizacion = intercambio.getRequestHeaders().getFirst("Authorization");
            if (autorizacion == null || !autorizacion.startsWith("Bearer ")) {
                intercambio.sendResponseHeaders(401, -1);
                return;
            }
            intercambio.getResponseHeaders().set("Content-Type", "application/json");
            byte[] datos = cuerpo.getBytes(StandardCharsets.UTF_8);
            intercambio.sendResponseHeaders(estado, datos.length);
            intercambio.getResponseBody().write(datos);
        }
    }

    private static String token(String rol) throws Exception {
        var base64 = Base64.getUrlEncoder().withoutPadding();
        long ahora = Instant.now().getEpochSecond();
        String cabecera = "{\"typ\":\"JWT\",\"alg\":\"HS256\"}";
        String cuerpo = """
            {"iss":"gasvolcan-auth","sub":"sesion@example.test","rol":"%s","iat":%d,"exp":%d}
            """.formatted(rol, ahora - 1, ahora + 300);
        String datos = base64.encodeToString(cabecera.getBytes(StandardCharsets.UTF_8)) + "."
                + base64.encodeToString(cuerpo.getBytes(StandardCharsets.UTF_8));
        Mac firma = Mac.getInstance("HmacSHA256");
        firma.init(new SecretKeySpec(new byte[32], "HmacSHA256"));
        return datos + "." + base64.encodeToString(firma.doFinal(datos.getBytes(StandardCharsets.UTF_8)));
    }
}
