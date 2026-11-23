package cl.duoc.gasvolcan.pedidos.repository;

import cl.duoc.gasvolcan.pedidos.model.Existencia;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExistenciaRepository extends JpaRepository<Existencia, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Existencia e where e.id = :id")
    Optional<Existencia> buscarParaActualizar(@Param("id") String id);
}
