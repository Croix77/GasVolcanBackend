package cl.duoc.gasvolcan.negocio.service;

import cl.duoc.gasvolcan.negocio.exception.CategoriaDuplicadaException;
import cl.duoc.gasvolcan.negocio.exception.RecursoNoEncontradoException;
import cl.duoc.gasvolcan.negocio.model.Categoria;
import cl.duoc.gasvolcan.negocio.repository.CategoriaRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CategoriaService {

    private final CategoriaRepository repository;

    // Spring entrega el repositorio al crear el servicio.
    public CategoriaService(CategoriaRepository repository) {
        this.repository = repository;
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
}
