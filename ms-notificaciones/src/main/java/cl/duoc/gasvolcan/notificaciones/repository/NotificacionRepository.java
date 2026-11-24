package cl.duoc.gasvolcan.notificaciones.repository;

import cl.duoc.gasvolcan.notificaciones.model.Notificacion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    List<Notificacion> findTop100ByOrderByIdDesc();

    Optional<Notificacion> findByIdempotencia(String idempotencia);
}
