package cl.duoc.gasvolcan.negocio.repository;

import cl.duoc.gasvolcan.negocio.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;

// Spring Data JPA proporciona las operaciones básicas sobre las categorías.
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    boolean existsByNombreIgnoreCase(String nombre);
}
