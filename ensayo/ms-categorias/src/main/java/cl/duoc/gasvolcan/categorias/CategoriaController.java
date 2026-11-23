package cl.duoc.gasvolcan.categorias;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/categorias")
class CategoriaController {

    private final CategoriaRepository categorias;

    CategoriaController(CategoriaRepository categorias) {
        this.categorias = categorias;
    }

    @GetMapping
    List<Categoria.Vista> listar() {
        return categorias.findAll(Sort.by("id")).stream().map(Categoria::vista).toList();
    }

    @GetMapping("/{id}")
    Categoria.Vista buscar(@PathVariable long id) {
        return categorias.findById(id).map(Categoria::vista)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
