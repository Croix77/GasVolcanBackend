package cl.duoc.gasvolcan.contacto;

import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

interface MensajeContactoRepository extends JpaRepository<MensajeContacto, Long> {

    Optional<MensajeContacto> findByCorreoAndReferencia(String correo, String referencia);

    Slice<MensajeContacto> findAllByOrderByFechaCreacionDescIdDesc(Pageable pagina);
}
