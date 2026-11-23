package cl.duoc.gasvolcan.negocio.service;

import cl.duoc.gasvolcan.negocio.exception.CategoriaDuplicadaException;
import cl.duoc.gasvolcan.negocio.exception.ConflictoNegocioException;
import cl.duoc.gasvolcan.negocio.exception.RecursoNoEncontradoException;
import cl.duoc.gasvolcan.negocio.model.Categoria;
import cl.duoc.gasvolcan.negocio.repository.CategoriaRepository;
import cl.duoc.gasvolcan.negocio.repository.ProductoRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoriaService {

    private final CategoriaRepository repository;
    private final ProductoRepository productos;

    public CategoriaService(CategoriaRepository repository, ProductoRepository productos) {
        this.repository = repository;
        this.productos = productos;
    }

    public List<Categoria> listar() {
        return repository.findAll();
    }

    public Categoria buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró la categoría con id " + id + "."));
    }

    public Categoria crear(String nombre) {
        if (repository.existsByNombreIgnoreCase(nombre)) {
            throw new CategoriaDuplicadaException();
        }

        Categoria categoria = new Categoria();
        categoria.setNombre(nombre);
        return repository.save(categoria);
    }

    @Transactional
    public Categoria actualizar(Long id, String nombre) {
        Categoria categoria = buscar(id);

        // El nombre de la propia categoría se puede conservar al editarla.
        if (repository.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw new CategoriaDuplicadaException();
        }

        categoria.setNombre(nombre);
        return repository.save(categoria);
    }

    @Transactional
    public void eliminar(Long id) {
        Categoria categoria = buscar(id);
        if (productos.existsByCategoria_Id(id)) {
            throw new ConflictoNegocioException("La categoría tiene productos asociados.");
        }
        repository.delete(categoria);
    }
}
