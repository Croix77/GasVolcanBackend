package cl.duoc.gasvolcan.negocio.controller;

import cl.duoc.gasvolcan.negocio.dto.CategoriaSolicitud;
import cl.duoc.gasvolcan.negocio.model.Categoria;
import cl.duoc.gasvolcan.negocio.service.CategoriaService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categorias")
public class CategoriaController {

    private final CategoriaService service;

    public CategoriaController(CategoriaService service) {
        this.service = service;
    }

    // Responde a GET /api/v1/categorias con el listado en formato JSON.
    @GetMapping
    public List<Categoria> listar() {
        return service.listar();
    }

    // El id de la URL indica qué categoría se debe consultar.
    @GetMapping("/{id}")
    public Categoria buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    public ResponseEntity<Categoria> crear(@Valid @RequestBody CategoriaSolicitud solicitud) {
        Categoria creada = service.crear(solicitud.nombre());
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }
}
