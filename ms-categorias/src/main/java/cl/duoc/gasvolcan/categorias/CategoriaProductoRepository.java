package cl.duoc.gasvolcan.categorias;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface CategoriaProductoRepository extends JpaRepository<CategoriaProducto, Long> {

    boolean existsByCategoria_Id(long categoriaId);

    Optional<CategoriaProducto> findByReferencia(String referencia);

    void deleteByReferencia(String referencia);
}
