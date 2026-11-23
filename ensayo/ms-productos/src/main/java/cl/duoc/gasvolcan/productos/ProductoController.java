package cl.duoc.gasvolcan.productos;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/productos")
class ProductoController {

    private final ProductoRepository productos;

    ProductoController(ProductoRepository productos) {
        this.productos = productos;
    }

    @GetMapping
    List<Producto.Vista> listar() {
        return productos.findAll(Sort.by("id")).stream().map(Producto::vista).toList();
    }

    @GetMapping("/{id}")
    Producto.Vista buscar(@PathVariable String id) {
        return productos.findById(id).map(Producto::vista).orElseThrow(ProductoController::noEncontrado);
    }

    @PutMapping("/{id}/precio")
    @Transactional
    Producto.Vista cambiarPrecio(@PathVariable String id, @RequestBody @Valid CambioPrecio solicitud) {
        Producto producto = productos.bloquear(id).orElseThrow(ProductoController::noEncontrado);
        producto.cambiarPrecio(solicitud.precio());
        return producto.vista();
    }

    private static ResponseStatusException noEncontrado() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe ese producto");
    }

    record CambioPrecio(@NotNull @PositiveOrZero Integer precio) {
    }
}
