package cl.duoc.gasvolcan.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cl.duoc.gasvolcan.auth.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

	private static final String SECRETO = Base64.getEncoder()
			.encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

	private JwtService jwtService;

	@BeforeEach
	void setUp() {
		jwtService = new JwtService();
		ReflectionTestUtils.setField(jwtService, "secret", SECRETO);
		ReflectionTestUtils.setField(jwtService, "issuer", "gasvolcan-auth");
		ReflectionTestUtils.setField(jwtService, "expirationMs", 3600000L);
	}

	@Test
	void generaUnTokenFirmadoConCorreoYRol() {
		Usuario usuario = new Usuario();
		usuario.setCorreo("admin@gaselvolcan.cl");
		usuario.setRol("ADMIN");

		String token = jwtService.generar(usuario);

		SecretKey clave = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRETO));
		Claims claims = Jwts.parser()
				.verifyWith(clave)
				.requireIssuer("gasvolcan-auth")
				.build()
				.parseSignedClaims(token)
				.getPayload();

		assertEquals("admin@gaselvolcan.cl", claims.getSubject());
		assertEquals("ADMIN", claims.get("rol", String.class));
	}
}
