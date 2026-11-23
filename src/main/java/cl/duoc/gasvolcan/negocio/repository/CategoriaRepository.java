package cl.duoc.gasvolcan.negocio.repository;

import cl.duoc.gasvolcan.negocio.model.Categoria;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// Spring Data JPA proporciona las operaciones básicas sobre las categorías.
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    boolean existsByNombreIgnoreCase(String nombre);

    Optional<Categoria> findByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
