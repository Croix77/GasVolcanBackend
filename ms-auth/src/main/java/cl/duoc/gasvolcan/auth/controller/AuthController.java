package cl.duoc.gasvolcan.auth.controller;

import cl.duoc.gasvolcan.auth.dto.LoginRequest;
import cl.duoc.gasvolcan.auth.dto.LoginResponse;
import cl.duoc.gasvolcan.auth.dto.UsuarioRespuesta;
import cl.duoc.gasvolcan.auth.service.AuthService;
import cl.duoc.gasvolcan.auth.service.UsuarioService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService auth;
	private final UsuarioService usuarios;

	public AuthController(AuthService auth, UsuarioService usuarios) {
		this.auth = auth;
		this.usuarios = usuarios;
	}

	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		return auth.login(request);
	}

	@GetMapping("/me")
	@SecurityRequirement(name = "bearerAuth")
	public UsuarioRespuesta perfil(@AuthenticationPrincipal Jwt token) {
		return auth.perfil(token);
	}

	@PostMapping("/refresh")
	@SecurityRequirement(name = "bearerAuth")
	public LoginResponse renovar(@AuthenticationPrincipal Jwt token) {
		return auth.renovar(token);
	}

	@GetMapping("/repartidores")
	@SecurityRequirement(name = "bearerAuth")
	public List<UsuarioRespuesta> repartidores() {
		return usuarios.repartidores();
	}
}
