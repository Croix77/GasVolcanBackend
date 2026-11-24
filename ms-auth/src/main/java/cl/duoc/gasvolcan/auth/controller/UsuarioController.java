package cl.duoc.gasvolcan.auth.controller;

import cl.duoc.gasvolcan.auth.dto.CrearUsuarioSolicitud;
import cl.duoc.gasvolcan.auth.dto.EditarUsuarioSolicitud;
import cl.duoc.gasvolcan.auth.dto.EstadoUsuarioSolicitud;
import cl.duoc.gasvolcan.auth.dto.UsuarioRespuesta;
import cl.duoc.gasvolcan.auth.service.UsuarioService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/usuarios")
@SecurityRequirement(name = "bearerAuth")
public class UsuarioController {

	private final UsuarioService usuarios;

	public UsuarioController(UsuarioService usuarios) {
		this.usuarios = usuarios;
	}

	@GetMapping
	public List<UsuarioRespuesta> listar() {
		return usuarios.listar();
	}

	@PostMapping
	public ResponseEntity<UsuarioRespuesta> crear(@Valid @RequestBody CrearUsuarioSolicitud solicitud) {
		UsuarioRespuesta creado = usuarios.crear(solicitud);
		return ResponseEntity.created(URI.create("/api/auth/usuarios/" + creado.id())).body(creado);
	}

	@GetMapping("/{id}")
	public UsuarioRespuesta detalle(@PathVariable Long id) {
		return usuarios.detalle(id);
	}

	@PutMapping("/{id}")
	public UsuarioRespuesta editar(@PathVariable Long id, @Valid @RequestBody EditarUsuarioSolicitud solicitud,
			Principal sesion) {
		return usuarios.editar(id, solicitud, sesion.getName());
	}

	@PatchMapping("/{id}/estado")
	public UsuarioRespuesta cambiarEstado(@PathVariable Long id, @Valid @RequestBody EstadoUsuarioSolicitud solicitud,
			Principal sesion) {
		return usuarios.cambiarEstado(id, solicitud.activo(), sesion.getName());
	}
}
