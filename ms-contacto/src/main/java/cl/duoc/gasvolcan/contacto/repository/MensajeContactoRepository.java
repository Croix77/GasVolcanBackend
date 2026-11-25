package cl.duoc.gasvolcan.contacto.repository;

import cl.duoc.gasvolcan.contacto.model.MensajeContacto;

import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MensajeContactoRepository extends JpaRepository<MensajeContacto, Long> {

    public Optional<MensajeContacto> findByCorreoAndReferencia(String correo, String referencia);

    public Slice<MensajeContacto> findAllByOrderByFechaCreacionDescIdDesc(Pageable pagina);
}
