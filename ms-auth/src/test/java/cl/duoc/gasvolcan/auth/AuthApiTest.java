package cl.duoc.gasvolcan.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cl.duoc.gasvolcan.auth.security.JwtService;
import io.jsonwebtoken.Jwts;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class AuthApiTest {

	private static final String ADMIN = "admin.prueba@example.test";
	private static final String PASSWORD = "Clave-de-integracion-17";
	private static final String BASE = "/api/auth";

	private final HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	@LocalServerPort
	private int puerto;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private JwtService jwt;

	@DynamicPropertySource
	static void configurar(DynamicPropertyRegistry configuracion) {
		byte[] secreto = new byte[32];
		new SecureRandom().nextBytes(secreto);
		configuracion.add("app.jwt.secret", () -> Base64.getEncoder().encodeToString(secreto));
		configuracion.add("spring.datasource.url", () -> "jdbc:h2:mem:ms_auth_pruebas;MODE=MySQL;DB_CLOSE_DELAY=-1");
		configuracion.add("spring.datasource.username", () -> "sa");
		configuracion.add("spring.datasource.password", () -> "");
		configuracion.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
		configuracion.add("app.bootstrap.admin.enabled", () -> "true");
		configuracion.add("app.bootstrap.admin.nombre", () -> "Administrador de prueba");
		configuracion.add("app.bootstrap.admin.correo", () -> ADMIN);
		configuracion.add("app.bootstrap.admin.password", () -> PASSWORD);
	}

	@Test
	void iniciaSesionYProtegeElPerfil() throws Exception {
		JsonNode login = login(ADMIN, PASSWORD);
		assertEquals("ADMIN", login.path("rol").asString());
		assertEquals("Administrador de prueba", login.path("nombre").asString());
		JsonNode perfil = enviar("GET", BASE + "/me", token(login), null, 200);
		assertEquals(ADMIN, perfil.path("correo").asString());
		assertTrue(perfil.path("activo").asBoolean());
		assertFalse(perfil.has("password"));
		assertTrue(enviar("GET", BASE + "/me", null, null, 401).has("mensaje"));
		enviar("POST", BASE + "/login", null, Map.of("correo", ADMIN, "password", "incorrecta"), 401);
	}

	@Test
	void administradorGestionaUsuariosConValidaciones() throws Exception {
		String admin = token(login(ADMIN, PASSWORD));
		String correo = "cliente.gestion@example.test";
		JsonNode usuario = crear(admin, correo, "CLIENTE");
		String ruta = BASE + "/usuarios/" + usuario.path("id").asLong();
		assertFalse(usuario.has("password"));
		assertEquals(correo, enviar("GET", ruta, admin, null, 200).path("correo").asString());
		assertTrue(enviar("GET", BASE + "/usuarios", admin, null, 200).isArray());

		JsonNode cambios = enviar("PUT", ruta, admin, Map.of("nombre", "Nombre actualizado", "rol", "VENDEDOR"), 200);
		assertEquals("Nombre actualizado", cambios.path("nombre").asString());
		assertEquals("VENDEDOR", cambios.path("rol").asString());
		assertEquals(correo, cambios.path("correo").asString());

		enviar("POST", BASE + "/usuarios", admin,
				Map.of("nombre", "Correo repetido", "correo", correo, "rol", "CLIENTE", "password", PASSWORD), 409);
		JsonNode errores = enviar("POST", BASE + "/usuarios", admin,
				Map.of("nombre", " ", "correo", "invalido", "rol", "CLIENTE", "password", "corta"), 400);
		assertTrue(errores.path("campos").has("correo"));
		enviar("GET", BASE + "/usuarios/99999", admin, null, 404);
	}

	@Test
	void desactivarYReactivarUnaCuentaNoReviveLaSesionAnterior() throws Exception {
		String admin = token(login(ADMIN, PASSWORD));
		String correo = "cliente.estado@example.test";
		JsonNode usuario = crear(admin, correo, "CLIENTE");
		String ruta = BASE + "/usuarios/" + usuario.path("id").asLong() + "/estado";
		String sesionAnterior = token(login(correo, PASSWORD));

		assertFalse(enviar("PATCH", ruta, admin, Map.of("activo", false), 200).path("activo").asBoolean());
		enviar("POST", BASE + "/login", null, Map.of("correo", correo, "password", PASSWORD), 401);
		assertTrue(enviar("GET", BASE + "/me", sesionAnterior, null, 401).has("mensaje"));

		assertTrue(enviar("PATCH", ruta, admin, Map.of("activo", true), 200).path("activo").asBoolean());
		enviar("GET", BASE + "/me", sesionAnterior, null, 401);
		String nuevaSesion = token(login(correo, PASSWORD));
		enviar("GET", BASE + "/me", nuevaSesion, null, 200);
	}

	@Test
	void separaPermisosDeAdministracionYReparto() throws Exception {
		String admin = token(login(ADMIN, PASSWORD));
		String correoOperadora = "operadora.permisos@example.test";
		String correoRepartidor = "repartidor.permisos@example.test";
		crear(admin, correoOperadora, "OPERADORA");
		crear(admin, correoRepartidor, "REPARTIDOR");
		String operadora = token(login(correoOperadora, PASSWORD));
		String repartidor = token(login(correoRepartidor, PASSWORD));
		JsonNode reparto = enviar("GET", BASE + "/repartidores", operadora, null, 200);
		assertTrue(reparto.isArray());
		assertTrue(reparto.toString().contains(correoRepartidor));
		assertFalse(reparto.toString().contains(correoOperadora));
		assertTrue(enviar("GET", BASE + "/usuarios", operadora, null, 403).has("mensaje"));
		enviar("GET", BASE + "/repartidores", repartidor, null, 403);
		long adminId = enviar("GET", BASE + "/me", admin, null, 200).path("id").asLong();
		enviar("PATCH", BASE + "/usuarios/" + adminId + "/estado", admin, Map.of("activo", false), 409);
	}

	@Test
	void renuevaCercaDelVencimientoYRechazaTokensCaducados() throws Exception {
		String actual = token(login(ADMIN, PASSWORD));
		assertEquals(actual, token(enviar("POST", BASE + "/refresh", actual, null, 200)));
		String proximoAVencer = tokenConVencimiento(Instant.now().plusSeconds(60));
		String renovado = token(enviar("POST", BASE + "/refresh", proximoAVencer, null, 200));
		assertNotEquals(proximoAVencer, renovado);
		enviar("GET", BASE + "/me", renovado, null, 200);
		enviar("POST", BASE + "/refresh", tokenConVencimiento(Instant.now().minusSeconds(5)), null, 401);
		enviar("POST", BASE + "/refresh", tokenConVencimiento(null), null, 401);
	}

	@Test
	void swaggerPublicaLasRutasYElEsquemaBearer() throws Exception {
		JsonNode documentacion = enviar("GET", "/v3/api-docs", null, null, 200);
		assertTrue(documentacion.path("paths").has(BASE + "/login"));
		assertTrue(documentacion.path("paths").has(BASE + "/usuarios/{id}/estado"));
		assertEquals("bearer", documentacion.path("components").path("securitySchemes")
				.path("bearerAuth").path("scheme").asString());
	}

	private JsonNode login(String correo, String password) throws Exception {
		return enviar("POST", BASE + "/login", null, Map.of("correo", correo, "password", password), 200);
	}

	private JsonNode crear(String admin, String correo, String rol) throws Exception {
		return enviar("POST", BASE + "/usuarios", admin,
				Map.of("nombre", "Usuario de prueba", "correo", correo, "rol", rol, "password", PASSWORD), 201);
	}

	private String token(JsonNode respuesta) {
		return respuesta.path("token").asString();
	}

	private String tokenConVencimiento(Instant vencimiento) {
		var firma = Jwts.builder().issuer("gasvolcan-auth").subject(ADMIN)
				.claim("rol", "ADMIN").claim("sesion", 0)
				.issuedAt(Date.from(Instant.now().minusSeconds(60)));
		if (vencimiento != null) {
			firma.expiration(Date.from(vencimiento));
		}
		return firma.signWith(jwt.clave(), Jwts.SIG.HS256).compact();
	}

	private JsonNode enviar(String metodo, String ruta, String token, Object cuerpo, int codigo) throws Exception {
		var peticion = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta))
				.timeout(Duration.ofSeconds(10)).header("Accept", "application/json");
		if (token != null) {
			peticion.header("Authorization", "Bearer " + token);
		}
		if (cuerpo == null) {
			peticion.method(metodo, HttpRequest.BodyPublishers.noBody());
		} else {
			peticion.header("Content-Type", "application/json")
					.method(metodo, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)));
		}
		var respuesta = cliente.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
		assertEquals(codigo, respuesta.statusCode(), metodo + " " + ruta);
		return json.readTree(respuesta.body());
	}
}
