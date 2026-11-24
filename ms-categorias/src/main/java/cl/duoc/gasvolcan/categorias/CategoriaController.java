package cl.duoc.gasvolcan.categorias;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categorias")
class CategoriaController {

    private final CategoriaService categorias;

    CategoriaController(CategoriaService categorias) {
        this.categorias = categorias;
    }

    @GetMapping
    List<Categoria.Vista> listar() {
        return categorias.listar();
    }

    @GetMapping("/{id}")
    Categoria.Vista buscar(@PathVariable long id) {
        return categorias.buscar(id);
    }

    @PostMapping
    ResponseEntity<Categoria.Vista> crear(@Valid @RequestBody Solicitud solicitud) {
        Categoria.Vista creada = categorias.crear(solicitud.nombre());
        return ResponseEntity.created(URI.create("/api/v1/categorias/" + creada.id())).body(creada);
    }

    @PutMapping("/{id}")
    Categoria.Vista actualizar(@PathVariable long id, @Valid @RequestBody Solicitud solicitud) {
        return categorias.actualizar(id, solicitud.nombre());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void eliminar(@PathVariable long id) {
        categorias.eliminar(id);
    }

    record Solicitud(
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 80, message = "El nombre admite hasta 80 caracteres.")
        String nombre
    ) {
    }
}
