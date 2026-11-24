package cl.duoc.gasvolcan.auth.service;

import cl.duoc.gasvolcan.auth.dto.LoginRequest;
import cl.duoc.gasvolcan.auth.dto.LoginResponse;
import cl.duoc.gasvolcan.auth.dto.UsuarioRespuesta;
import cl.duoc.gasvolcan.auth.model.Usuario;
import cl.duoc.gasvolcan.auth.repository.UsuarioRepository;
import cl.duoc.gasvolcan.auth.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

	private final UsuarioRepository usuarios;
	private final PasswordEncoder encoder;
	private final JwtService jwt;

	@Value("${app.jwt.refresh-window-ms}")
	private long ventanaRenovacion;

	public AuthService(UsuarioRepository usuarios, PasswordEncoder encoder, JwtService jwt) {
		this.usuarios = usuarios;
		this.encoder = encoder;
		this.jwt = jwt;
	}

	public LoginResponse login(LoginRequest request) {
		Usuario usuario = usuarios.findByCorreoIgnoreCase(request.correo())
				.orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));
		if (!usuario.isHabilitado() || request.password().getBytes(StandardCharsets.UTF_8).length > 72
				|| !encoder.matches(request.password(), usuario.getPassword())) {
			throw new BadCredentialsException("Credenciales inválidas");
		}
		return respuesta(usuario, jwt.generar(usuario));
	}

	public UsuarioRespuesta perfil(Jwt token) {
		return UsuarioRespuesta.desde(consultarSesion(token));
	}

	public LoginResponse renovar(Jwt token) {
		Usuario usuario = consultarSesion(token);
		Instant ahora = Instant.now();
		if (token.getExpiresAt() == null || !token.getExpiresAt().isAfter(ahora)) {
			throw new BadCredentialsException("Sesión vencida");
		}
		if (token.getExpiresAt().isAfter(ahora.plusMillis(ventanaRenovacion))) {
			// React puede comprobar la sesión antes de llegar al período de renovación.
			return respuesta(usuario, token.getTokenValue());
		}
		return respuesta(usuario, jwt.generar(usuario));
	}

	private Usuario consultarSesion(Jwt token) {
		return usuarios.findByCorreoIgnoreCase(token.getSubject())
				.filter(usuario -> coincideSesion(token, usuario))
				.orElseThrow(() -> new BadCredentialsException("Sesión inválida"));
	}

	public static boolean coincideSesion(Jwt token, Usuario usuario) {
		Object version = token.getClaims().get("sesion");
		return usuario.isHabilitado()
				&& usuario.getRol().equals(token.getClaimAsString("rol"))
				&& version instanceof Number numero
				&& usuario.getVersionSesion() == numero.longValue();
	}

	private LoginResponse respuesta(Usuario usuario, String token) {
		return new LoginResponse(token, usuario.getCorreo(), usuario.getRol(), usuario.nombreVisible());
	}
}
