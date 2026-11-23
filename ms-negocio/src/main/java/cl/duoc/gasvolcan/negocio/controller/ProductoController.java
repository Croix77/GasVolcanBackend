package cl.duoc.gasvolcan.negocio.controller;

import cl.duoc.gasvolcan.negocio.dto.ProductoRespuesta;
import cl.duoc.gasvolcan.negocio.dto.ProductoSolicitud;
import cl.duoc.gasvolcan.negocio.service.ProductoService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/productos")
public class ProductoController {

    private final ProductoService service;

    public ProductoController(ProductoService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProductoRespuesta> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public ProductoRespuesta buscar(@PathVariable String id) {
        return service.buscar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductoRespuesta crear(@Valid @RequestBody ProductoSolicitud solicitud) {
        return service.crear(solicitud);
    }

    @PutMapping("/{id}")
    public ProductoRespuesta actualizar(@PathVariable String id, @Valid @RequestBody ProductoSolicitud solicitud) {
        return service.actualizar(id, solicitud);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable String id) {
        service.eliminar(id);
    }
}
