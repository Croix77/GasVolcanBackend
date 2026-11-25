package cl.duoc.gasvolcan.productos.controller;

import cl.duoc.gasvolcan.productos.dto.ProductoRespuesta;
import cl.duoc.gasvolcan.productos.dto.ProductoSolicitud;
import cl.duoc.gasvolcan.productos.model.Producto;
import cl.duoc.gasvolcan.productos.service.ProductoService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
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
@RequestMapping("/api/v1/productos")
public class ProductoController {

    private final ProductoService productos;

    public ProductoController(ProductoService productos) {
        this.productos = productos;
    }

    @GetMapping
    public List<ProductoRespuesta> listar() {
        return productos.listar();
    }

    @GetMapping("/{id}")
    public ProductoRespuesta buscar(@PathVariable String id) {
        return productos.buscar(id);
    }

    @GetMapping("/{id}/precio")
    public Producto.PrecioVenta precio(@PathVariable String id) {
        return productos.precioVenta(id);
    }

    @PostMapping
    public ResponseEntity<ProductoRespuesta> crear(@Valid @RequestBody ProductoSolicitud solicitud) {
        ProductoRespuesta creado = productos.crear(solicitud);
        return ResponseEntity.created(URI.create("/api/v1/productos/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    public ProductoRespuesta actualizar(@PathVariable String id, @Valid @RequestBody ProductoSolicitud solicitud) {
        return productos.actualizar(id, solicitud);
    }

    @PutMapping("/{id}/precio")
    public Producto.PrecioVenta cambiarPrecio(@PathVariable String id, @Valid @RequestBody PrecioSolicitud solicitud) {
        return productos.cambiarPrecio(id, solicitud.precio());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable String id) {
        productos.eliminar(id);
    }

    public record PrecioSolicitud(
            @NotNull(message = "El precio es obligatorio.")
            @PositiveOrZero(message = "El precio debe ser mayor o igual a cero.")
            Integer precio
    ) {
    }
}
