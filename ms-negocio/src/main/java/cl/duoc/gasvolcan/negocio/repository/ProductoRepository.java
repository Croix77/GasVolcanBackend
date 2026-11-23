package cl.duoc.gasvolcan.negocio.repository;

import cl.duoc.gasvolcan.negocio.model.Producto;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductoRepository extends JpaRepository<Producto, String> {

    @EntityGraph(attributePaths = "categoria")
    List<Producto> findByActivoTrueOrderByNombreAscIdAsc();

    Optional<Producto> findByIdAndActivoTrue(String id);

    boolean existsByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCaseAndIdNot(String codigo, String id);

    boolean existsByCategoria_Id(Long categoriaId);

    // Los pedidos y la administración modifican stock con el mismo bloqueo por producto.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Producto p where p.id = :id")
    Optional<Producto> buscarParaActualizar(@Param("id") String id);
}
