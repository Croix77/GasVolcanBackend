package cl.duoc.gasvolcan.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cl.duoc.gasvolcan.auth.dto.LoginRequest;
import cl.duoc.gasvolcan.auth.dto.LoginResponse;
import cl.duoc.gasvolcan.auth.model.Usuario;
import cl.duoc.gasvolcan.auth.repository.UsuarioRepository;
import cl.duoc.gasvolcan.auth.security.JwtService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private UsuarioRepository usuarios;

	@Mock
	private PasswordEncoder encoder;

	@Mock
	private JwtService jwt;

	@InjectMocks
	private AuthService authService;

	@Test
	void devuelveTokenCuandoLasCredencialesSonValidas() {
		Usuario usuario = usuario("ADMIN", true);
		when(usuarios.findByCorreoIgnoreCase("admin@gaselvolcan.cl")).thenReturn(Optional.of(usuario));
		when(encoder.matches("Admin123!", "hash")).thenReturn(true);
		when(jwt.generar(usuario)).thenReturn("token-firmado");

		LoginResponse respuesta = authService.login(new LoginRequest("admin@gaselvolcan.cl", "Admin123!"));

		assertEquals("token-firmado", respuesta.token());
		assertEquals("admin@gaselvolcan.cl", respuesta.correo());
		assertEquals("ADMIN", respuesta.rol());
	}

	@Test
	void rechazaPasswordIncorrecta() {
		Usuario usuario = usuario("ADMIN", true);
		when(usuarios.findByCorreoIgnoreCase("admin@gaselvolcan.cl")).thenReturn(Optional.of(usuario));
		when(encoder.matches("mala", "hash")).thenReturn(false);

		assertThrows(BadCredentialsException.class,
				() -> authService.login(new LoginRequest("admin@gaselvolcan.cl", "mala")));
		verify(jwt, never()).generar(usuario);
	}

	@Test
	void rechazaCuentaDeshabilitada() {
		Usuario usuario = usuario("CLIENTE", false);
		when(usuarios.findByCorreoIgnoreCase("cliente@gaselvolcan.cl")).thenReturn(Optional.of(usuario));

		assertThrows(BadCredentialsException.class,
				() -> authService.login(new LoginRequest("cliente@gaselvolcan.cl", "Cliente123!")));
		verify(encoder, never()).matches("Cliente123!", "hash");
	}

	@Test
	void rechazaCorreoInexistente() {
		when(usuarios.findByCorreoIgnoreCase("nadie@gaselvolcan.cl")).thenReturn(Optional.empty());

		assertThrows(BadCredentialsException.class,
				() -> authService.login(new LoginRequest("nadie@gaselvolcan.cl", "x")));
	}

	private Usuario usuario(String rol, boolean habilitado) {
		Usuario usuario = new Usuario();
		usuario.setCorreo(rol.equals("ADMIN") ? "admin@gaselvolcan.cl"
				: rol.equals("CLIENTE") ? "cliente@gaselvolcan.cl" : "vendedor@gaselvolcan.cl");
		usuario.setPassword("hash");
		usuario.setRol(rol);
		usuario.setHabilitado(habilitado);
		return usuario;
	}
}
