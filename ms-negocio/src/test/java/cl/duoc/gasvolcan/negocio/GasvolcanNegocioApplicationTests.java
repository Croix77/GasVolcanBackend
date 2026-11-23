package cl.duoc.gasvolcan.negocio;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:negocio_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.seed.catalogo=true",
    "app.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
class GasvolcanNegocioApplicationTests {

    @LocalServerPort
    int puerto;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void protegeApiYDocumentaLosEndpoints() throws Exception {
        enviar("GET", "/api/v1/productos", null, null, 401);
        enviar("GET", "/api/v1/productos", token("CLIENTE", "cliente@example.test", -90), null, 401);
        String vendedor = token("VENDEDOR", "vendedor@example.test");
        JsonNode productos = enviar("GET", "/api/v1/productos", vendedor, null, 200);
        assertThat(productos.size()).isGreaterThanOrEqualTo(14);
        assertThat(enviar("GET", "/api/v1/zonas", vendedor, null, 200).size()).isEqualTo(5);
        enviar("POST", "/api/v1/categorias", vendedor, "{\"nombre\":\"Sin permiso\"}", 403);

        var documentacion = enviar("GET", "/v3/api-docs", null, null, 200);
        assertThat(documentacion.path("paths").has("/api/v1/pedidos")).isTrue();
    }

    @Test
    void persisteCrudYRespetaLaCategoriaEnUso() throws Exception {
        String admin = token("ADMIN", "admin@example.test");
        String categoria = "/api/v1/categorias";
        String producto = "/api/v1/productos";

        long id = enviar("POST", categoria, admin, "{\"nombre\":\"Prueba catálogo\"}", 201).path("id").asLong();
        String datos = """
            {"codigo":"regulador-prueba","nombre":"Regulador de prueba","descripcion":"Accesorio",
             "unidad":"Unidad","categoriaId":%s,"precio":0,"stock":4,"version":0}
            """.formatted(id);
        assertThat(enviar("POST", producto, admin, datos, 201).path("precio").asInt()).isZero();
        enviar("PUT", categoria + "/" + id, admin, "{\"nombre\":\"Reguladores de prueba\"}", 200);
        assertThat(enviar("GET", producto + "/regulador-prueba", admin, null, 200)
            .path("categoria").asText()).isEqualTo("Reguladores de prueba");
        enviar("PUT", producto + "/regulador-prueba", admin, datos.replace("\"stock\":4", "\"stock\":3.5"), 400);
        assertThat(enviar("PUT", producto + "/regulador-prueba", admin, datos.replace("\"stock\":4", "\"stock\":5"), 200)
            .path("stock").asInt()).isEqualTo(5);
        enviar("PUT", producto + "/regulador-prueba", admin, datos, 409);
        enviar("DELETE", categoria + "/" + id, admin, null, 409);
        enviar("DELETE", producto + "/regulador-prueba", admin, null, 204);
        enviar("GET", producto + "/regulador-prueba", admin, null, 404);
        enviar("DELETE", categoria + "/" + id, admin, null, 409);
        long vacia = enviar("POST", categoria, admin, "{\"nombre\":\"Temporal vacía\"}", 201).path("id").asLong();
        enviar("DELETE", categoria + "/" + vacia, admin, null, 204);
    }

    @Test
    void validaCantidadesYRevierteStockAlFallarUnArticulo() throws Exception {
        String cliente = token("CLIENTE", "comprador@example.test");
        String pedidos = "/api/v1/pedidos";
        String producto = "/api/v1/productos/gas-15kg";
        int stock = enviar("GET", producto, cliente, null, 200).path("stock").asInt();
        enviar("POST", pedidos, cliente, datosPedido("""
            [{"id":"gas-15kg","cantidad":2},{"id":"gas-15kg","cantidad":2}]
            """), 400);
        enviar("POST", pedidos, cliente, datosPedido("""
            [{"id":"gas-15kg","cantidad":1},{"id":"z-no-existe","cantidad":1}]
            """), 404);
        assertThat(enviar("GET", producto, cliente, null, 200).path("stock").asInt()).isEqualTo(stock);
    }

    @Test
    void registraCompraDespachoYpagoRespetandoElRolYElPropietario() throws Exception {
        String cliente = token("CLIENTE", "cliente@example.test");
        String operadora = token("OPERADORA", "operadora@example.test");
        String repartidor = token("REPARTIDOR", "repartidor@example.test");
        String ajeno = token("REPARTIDOR", "otro@example.test");
        String ruta = "/api/v1/productos/gas-11kg";
        JsonNode producto = enviar("GET", ruta, cliente, null, 200);
        JsonNode pedido = enviar("POST", "/api/v1/pedidos", cliente,
            datosPedido("[{\"id\":\"gas-11kg\",\"cantidad\":2}]"), 201);
        String detalle = "/api/v1/pedidos/" + pedido.path("id").asLong();

        assertThat(pedido.path("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(pedido.path("total").asInt()).isEqualTo(producto.path("precio").asInt() * 2);
        assertThat(enviar("GET", ruta, cliente, null, 200).path("stock").asInt())
            .isEqualTo(producto.path("stock").asInt() - 2);
        enviar("GET", detalle, token("CLIENTE", "otra@example.test"), null, 404);
        enviar("GET", detalle, repartidor, null, 404);

        String asignacion = "{\"correoRepartidor\":\"repartidor@example.test\"}";
        enviar("PATCH", detalle + "/asignacion", cliente, asignacion, 403);
        assertThat(enviar("PATCH", detalle + "/asignacion", operadora, asignacion, 200)
            .path("estado").asText()).isEqualTo("ASIGNADO");
        enviar("GET", detalle, repartidor, null, 200);
        enviar("PATCH", detalle + "/estado", ajeno, "{\"estado\":\"EN_CAMINO\"}", 403);
        enviar("PATCH", detalle + "/estado", repartidor, "{\"estado\":\"ENTREGADO\"}", 409);
        enviar("PATCH", detalle + "/estado", repartidor, "{\"estado\":\"EN_CAMINO\"}", 200);
        enviar("PUT", detalle + "/ubicacion", repartidor, "{\"latitud\":-36.61,\"longitud\":-72.10}", 200);
        assertThat(enviar("GET", detalle, cliente, null, 200).path("latitudRepartidor").asDouble()).isEqualTo(-36.61);
        JsonNode entregado = enviar("PATCH", detalle + "/estado", repartidor, "{\"estado\":\"ENTREGADO\"}", 200);
        assertThat(entregado.path("estado").asText()).isEqualTo("ENTREGADO");
        assertThat(entregado.path("pagoEfectivo").asBoolean()).isTrue();
    }

    @Test
    void reintentaUnaCompraSinCrearOtroPedidoNiDescontarStock() throws Exception {
        String cliente = token("CLIENTE", "reintento@example.test");
        String producto = "/api/v1/productos/gas-5kg";
        String pedidos = "/api/v1/pedidos";
        int stock = enviar("GET", producto, cliente, null, 200).path("stock").asInt();
        String datos = datosPedido("[{\"id\":\"gas-5kg\",\"cantidad\":2}]")
            .replace("\"items\":", "\"referenciaCompra\":\"6a40b7cf-a925-442a-958f-53c89ca52cc1\",\"items\":");

        JsonNode compra = enviar("POST", pedidos, cliente, datos, 201);
        JsonNode reintento = enviar("POST", pedidos, cliente, datos, 200);
        assertThat(reintento.path("id").asLong()).isEqualTo(compra.path("id").asLong());
        assertThat(reintento.path("items").size()).isEqualTo(1);
        assertThat(reintento.path("items").get(0).path("cantidad").asInt()).isEqualTo(2);
        assertThat(enviar("GET", pedidos, cliente, null, 200).size()).isEqualTo(1);
        assertThat(enviar("GET", producto, cliente, null, 200).path("stock").asInt()).isEqualTo(stock - 2);
    }

    @Test
    void exigeCoordenadasParaCrearPedido() throws Exception {
        String cliente = token("CLIENTE", "sin-punto@example.test");
        String pedidos = "/api/v1/pedidos";
        String datos = datosPedido("[{\"id\":\"gas-5kg\",\"cantidad\":1}]");
        enviar("POST", pedidos, cliente, datos.replace("-36.61", "null"), 400);
        enviar("POST", pedidos, cliente, datos.replace("-72.10", "-181"), 400);
        assertThat(enviar("GET", pedidos, cliente, null, 200).size()).isZero();
    }

    private String datosPedido(String items) {
        return """
            {"nombre":"Cliente Prueba","telefono":"+56 9 1234 5678",
             "direccion":"Calle de prueba 123","comuna":"Chillán","items":%s,
             "latitudEntrega":-36.61,"longitudEntrega":-72.10}
            """.formatted(items);
    }

    private JsonNode enviar(String metodo, String ruta, String token, String datos, int esperado) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
            .method(metodo, datos == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(datos));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (datos != null) request.header("Content-Type", "application/json");
        var respuesta = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(respuesta.statusCode()).as("%s %s %s", metodo, ruta, respuesta.body()).isEqualTo(esperado);
        return respuesta.body().isBlank() ? json.createObjectNode() : json.readTree(respuesta.body());
    }

    private String token(String rol, String correo) throws Exception {
        return token(rol, correo, 3600);
    }

    // Estas claves y cuentas existen solo en la base aislada de esta prueba.
    private String token(String rol, String correo, long validez) throws Exception {
        var base64 = Base64.getUrlEncoder().withoutPadding();
        long ahora = Instant.now().getEpochSecond();
        String cabecera = "{\"typ\":\"JWT\",\"alg\":\"HS256\"}";
        String cuerpo = """
            {"iss":"gasvolcan-auth","sub":"%s","rol":"%s","iat":%d,"exp":%d}
            """.formatted(correo, rol, ahora - 120, ahora + validez);
        String datos = base64.encodeToString(cabecera.getBytes(StandardCharsets.UTF_8)) + "."
            + base64.encodeToString(cuerpo.getBytes(StandardCharsets.UTF_8));
        Mac firma = Mac.getInstance("HmacSHA256");
        firma.init(new SecretKeySpec(new byte[32], "HmacSHA256"));
        return datos + "." + base64.encodeToString(firma.doFinal(datos.getBytes(StandardCharsets.UTF_8)));
    }
}
