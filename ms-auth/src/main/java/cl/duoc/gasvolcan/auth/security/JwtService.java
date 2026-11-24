package cl.duoc.gasvolcan.auth.security;

import cl.duoc.gasvolcan.auth.model.Usuario;
import io.jsonwebtoken.Jwts;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

	@Value("${app.jwt.secret}")
	private String secret;

	@Value("${app.jwt.issuer}")
	private String issuer;

	@Value("${app.jwt.expiration-ms}")
	private long expirationMs;

	public SecretKey clave() {
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(secret);
		} catch (IllegalArgumentException ex) {
			throw new IllegalStateException("JWT_SECRET debe estar codificada en Base64.");
		}
		if (bytes.length < 32) {
			throw new IllegalStateException("JWT_SECRET debe representar al menos 32 bytes.");
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

	public String generar(Usuario usuario) {
		Date ahora = new Date();
		return Jwts.builder()
				.issuer(issuer)
				.id(UUID.randomUUID().toString())
				.subject(usuario.getCorreo())
				.claim("rol", usuario.getRol())
				.claim("sesion", usuario.getVersionSesion())
				.issuedAt(ahora)
				.expiration(new Date(ahora.getTime() + expirationMs))
				.signWith(clave(), Jwts.SIG.HS256)
				.compact();
	}
}
