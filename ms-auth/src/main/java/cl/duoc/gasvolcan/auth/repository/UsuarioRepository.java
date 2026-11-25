package cl.duoc.gasvolcan.auth.repository;

import cl.duoc.gasvolcan.auth.model.Usuario;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	Optional<Usuario> findByCorreoIgnoreCase(String correo);

	boolean existsByRolAndHabilitadoTrue(String rol);
	List<Usuario> findAllByRolAndHabilitadoTrueOrderByNombreAscCorreoAsc(String rol);
}
