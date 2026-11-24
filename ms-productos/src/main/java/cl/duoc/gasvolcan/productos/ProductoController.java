package cl.duoc.gasvolcan.productos;

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
class ProductoController {

    private final ProductoService productos;

    ProductoController(ProductoService productos) {
        this.productos = productos;
    }

    @GetMapping
    List<ProductoRespuesta> listar() {
        return productos.listar();
    }

    @GetMapping("/{id}")
    ProductoRespuesta buscar(@PathVariable String id) {
        return productos.buscar(id);
    }

    @GetMapping("/{id}/precio")
    Producto.PrecioVenta precio(@PathVariable String id) {
        return productos.precioVenta(id);
    }

    @PostMapping
    ResponseEntity<ProductoRespuesta> crear(@Valid @RequestBody ProductoSolicitud solicitud) {
        ProductoRespuesta creado = productos.crear(solicitud);
        return ResponseEntity.created(URI.create("/api/v1/productos/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    ProductoRespuesta actualizar(@PathVariable String id, @Valid @RequestBody ProductoSolicitud solicitud) {
        return productos.actualizar(id, solicitud);
    }

    @PutMapping("/{id}/precio")
    Producto.PrecioVenta cambiarPrecio(@PathVariable String id, @Valid @RequestBody PrecioSolicitud solicitud) {
        return productos.cambiarPrecio(id, solicitud.precio());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void eliminar(@PathVariable String id) {
        productos.eliminar(id);
    }

    record PrecioSolicitud(
            @NotNull(message = "El precio es obligatorio.")
            @PositiveOrZero(message = "El precio debe ser mayor o igual a cero.")
            Integer precio
    ) {
    }
}
