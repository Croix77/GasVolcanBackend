package cl.duoc.gasvolcan.auth.config;

import cl.duoc.gasvolcan.auth.dto.CrearUsuarioSolicitud;
import cl.duoc.gasvolcan.auth.model.Rol;
import cl.duoc.gasvolcan.auth.repository.UsuarioRepository;
import cl.duoc.gasvolcan.auth.service.UsuarioService;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.bootstrap.admin.enabled", havingValue = "true")
public class DatosIniciales {

	@Bean
	CommandLineRunner usuarioInicial(UsuarioRepository repo, UsuarioService usuarios, Validator validator,
			@Value("${app.bootstrap.admin.nombre}") String nombre,
			@Value("${app.bootstrap.admin.correo}") String correo,
			@Value("${app.bootstrap.admin.password}") String password) {
		return args -> {
			if (repo.existsByRolAndHabilitadoTrue(Rol.ADMIN.name())) {
				return;
			}
			CrearUsuarioSolicitud admin = new CrearUsuarioSolicitud(nombre, correo, Rol.ADMIN, password);
			if (!validator.validate(admin).isEmpty()) {
				throw new IllegalStateException("Completa nombre, correo y contraseña del administrador inicial.");
			}
			// El arranque inicial no modifica una cuenta existente.
			if (repo.findByCorreoIgnoreCase(correo.strip()).isPresent()) {
				throw new IllegalStateException("El correo del administrador inicial ya está en uso.");
			}
			usuarios.crear(admin);
		};
	}
}
