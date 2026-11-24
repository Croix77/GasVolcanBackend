package cl.duoc.gasvolcan.categorias;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class CategoriaService {

    private final CategoriaRepository categorias;
    private final CategoriaProductoRepository referencias;

    CategoriaService(CategoriaRepository categorias, CategoriaProductoRepository referencias) {
        this.categorias = categorias;
        this.referencias = referencias;
    }

    @Transactional(readOnly = true)
    List<Categoria.Vista> listar() {
        return categorias.findAllByOrderByIdAsc().stream().map(Categoria::vista).toList();
    }

    @Transactional(readOnly = true)
    Categoria.Vista buscar(long id) {
        return categorias.findById(id).orElseThrow(() -> inexistente(id)).vista();
    }

    @Transactional
    Categoria.Vista crear(String nombre) {
        String limpio = nombre.strip();
        if (categorias.existsByNombreIgnoreCase(limpio)) {
            throw nombreOcupado();
        }
        return categorias.save(new Categoria(limpio)).vista();
    }

    @Transactional
    Categoria.Vista actualizar(long id, String nombre) {
        Categoria categoria = bloquear(id);
        String limpio = nombre.strip();
        if (categorias.existsByNombreIgnoreCaseAndIdNot(limpio, id)) {
            throw nombreOcupado();
        }
        categoria.cambiarNombre(limpio);
        return categoria.vista();
    }

    @Transactional
    void eliminar(long id) {
        Categoria categoria = bloquear(id);
        if (referencias.existsByCategoria_Id(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La categoría tiene productos asociados.");
        }
        categorias.delete(categoria);
    }

    @Transactional
    Categoria.Vista asociar(long id, String productoId, String referencia) {
        // Asociar y eliminar toman el mismo bloqueo para que un producto no quede sin categoría.
        Categoria categoria = bloquear(id);
        var actual = referencias.findByReferencia(referencia);
        if (actual.isPresent()) {
            if (actual.get().coincide(categoria, productoId)) {
                return categoria.vista();
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La referencia del producto ya está en uso.");
        }
        referencias.save(new CategoriaProducto(categoria, productoId, referencia));
        return categoria.vista();
    }

    @Transactional
    void liberar(String referencia) {
        referencias.deleteByReferencia(referencia);
    }

    private Categoria bloquear(long id) {
        return categorias.bloquear(id).orElseThrow(() -> inexistente(id));
    }

    private ResponseStatusException inexistente(long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró la categoría con id " + id + ".");
    }

    private ResponseStatusException nombreOcupado() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una categoría con ese nombre.");
    }
}
