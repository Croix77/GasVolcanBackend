package cl.duoc.gasvolcan.categorias;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/api/v1/interno/categorias")
class CategoriaInternaController {

    private final CategoriaService categorias;

    CategoriaInternaController(CategoriaService categorias) {
        this.categorias = categorias;
    }

    @GetMapping
    List<Categoria.Vista> listar() {
        return categorias.listar();
    }

    @PutMapping("/{id}/referencias/{referencia}")
    Categoria.Vista asociar(@PathVariable long id, @PathVariable String referencia,
                             @Valid @RequestBody Asociacion solicitud) {
        return categorias.asociar(id, solicitud.productoId(), referencia);
    }

    @DeleteMapping("/referencias/{referencia}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void liberar(@PathVariable String referencia) {
        categorias.liberar(referencia);
    }

    record Asociacion(@NotBlank @Size(max = 40) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String productoId) {
    }
}
