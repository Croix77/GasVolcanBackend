package cl.duoc.gasvolcan.auth.service;

import cl.duoc.gasvolcan.auth.dto.CrearUsuarioSolicitud;
import cl.duoc.gasvolcan.auth.dto.EditarUsuarioSolicitud;
import cl.duoc.gasvolcan.auth.dto.UsuarioRespuesta;
import cl.duoc.gasvolcan.auth.model.Rol;
import cl.duoc.gasvolcan.auth.model.Usuario;
import cl.duoc.gasvolcan.auth.repository.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UsuarioService {

	private final UsuarioRepository usuarios;
	private final PasswordEncoder encoder;

	public UsuarioService(UsuarioRepository usuarios, PasswordEncoder encoder) {
		this.usuarios = usuarios;
		this.encoder = encoder;
	}

	public List<UsuarioRespuesta> listar() {
		return usuarios.findAll(Sort.by("nombre", "correo")).stream().map(UsuarioRespuesta::desde).toList();
	}

	public UsuarioRespuesta detalle(Long id) {
		return UsuarioRespuesta.desde(buscar(id));
	}

	public List<UsuarioRespuesta> repartidores() {
		return usuarios.findAllByRolAndHabilitadoTrueOrderByNombreAscCorreoAsc(Rol.REPARTIDOR.name())
				.stream().map(UsuarioRespuesta::desde).toList();
	}

	@Transactional
	public UsuarioRespuesta crear(CrearUsuarioSolicitud solicitud) {
		String correo = solicitud.correo().strip().toLowerCase(Locale.ROOT);
		if (usuarios.findByCorreoIgnoreCase(correo).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo ya está registrado.");
		}
		Usuario usuario = new Usuario();
		usuario.setNombre(solicitud.nombre().strip());
		usuario.setCorreo(correo);
		usuario.setRol(solicitud.rol().name());
		usuario.setPassword(codificar(solicitud.password()));
		return UsuarioRespuesta.desde(usuarios.save(usuario));
	}

	@Transactional
	public UsuarioRespuesta editar(Long id, EditarUsuarioSolicitud solicitud, String correoSesion) {
		Usuario usuario = buscar(id);
		String rol = solicitud.rol().name();
		boolean cambioRol = !usuario.getRol().equals(rol);
		if (cambioRol && usuario.getCorreo().equalsIgnoreCase(correoSesion)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Tu cuenta no puede cambiar su propio rol.");
		}
		usuario.setNombre(solicitud.nombre().strip());
		usuario.setRol(rol);
		if (solicitud.password() != null) {
			usuario.setPassword(codificar(solicitud.password()));
		}
		if (cambioRol || solicitud.password() != null) {
			usuario.invalidarSesiones();
		}
		return UsuarioRespuesta.desde(usuario);
	}

	@Transactional
	public UsuarioRespuesta cambiarEstado(Long id, boolean activo, String correoSesion) {
		Usuario usuario = buscar(id);
		if (!activo && usuario.getCorreo().equalsIgnoreCase(correoSesion)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Tu cuenta no puede desactivarse a sí misma.");
		}
		if (usuario.isHabilitado() != activo) {
			usuario.setHabilitado(activo);
			usuario.invalidarSesiones();
		}
		return UsuarioRespuesta.desde(usuario);
	}

	private Usuario buscar(Long id) {
		return usuarios.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado."));
	}

	private String codificar(String password) {
		if (password.isBlank() || password.getBytes(StandardCharsets.UTF_8).length > 72) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña debe usar hasta 72 bytes y no puede estar vacía.");
		}
		return encoder.encode(password);
	}
}
