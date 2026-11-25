package cl.duoc.gasvolcan.productos.repository;

import cl.duoc.gasvolcan.productos.model.Producto;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductoRepository extends JpaRepository<Producto, String> {

    public List<Producto> findAllByActivoTrueOrderByNombreAscIdAsc();

    public Optional<Producto> findByIdAndActivoTrue(String id);

    public boolean existsByCodigoIgnoreCase(String codigo);

    public boolean existsByCodigoIgnoreCaseAndIdNot(String codigo, String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Producto p where p.id = :id")
    public Optional<Producto> bloquear(@Param("id") String id);
}
