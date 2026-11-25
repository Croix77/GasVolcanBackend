package cl.duoc.gasvolcan.categorias.repository;

import cl.duoc.gasvolcan.categorias.model.CategoriaProducto;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaProductoRepository extends JpaRepository<CategoriaProducto, Long> {

    public boolean existsByCategoria_Id(long categoriaId);

    public Optional<CategoriaProducto> findByReferencia(String referencia);

    public void deleteByReferencia(String referencia);
}
