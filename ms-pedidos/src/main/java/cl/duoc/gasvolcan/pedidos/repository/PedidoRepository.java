package cl.duoc.gasvolcan.pedidos.repository;

import cl.duoc.gasvolcan.pedidos.model.Pedido;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PedidoRepository extends JpaRepository<Pedido, Long>, JpaSpecificationExecutor<Pedido> {

    @Override
    @EntityGraph(attributePaths = "items")
    List<Pedido> findAll(Specification<Pedido> alcance, Sort orden);

    @EntityGraph(attributePaths = "items")
    Optional<Pedido> findByCorreoClienteAndReferenciaCompra(String correoCliente, String referenciaCompra);

    // Evita aplicar dos cambios de estado al mismo pedido al mismo tiempo.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pedido p where p.id = :id")
    Optional<Pedido> buscarParaActualizar(@Param("id") Long id);
}
