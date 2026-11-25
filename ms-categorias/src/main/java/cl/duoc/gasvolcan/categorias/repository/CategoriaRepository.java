package cl.duoc.gasvolcan.categorias.repository;

import cl.duoc.gasvolcan.categorias.model.Categoria;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    public List<Categoria> findAllByOrderByIdAsc();

    public boolean existsByNombreIgnoreCase(String nombre);

    public boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Categoria c where c.id = :id")
    public Optional<Categoria> bloquear(@Param("id") Long id);
}
