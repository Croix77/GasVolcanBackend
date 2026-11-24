package cl.duoc.gasvolcan.pedidos.controller;

import cl.duoc.gasvolcan.pedidos.dto.StockRespuesta;
import cl.duoc.gasvolcan.pedidos.dto.StockSolicitud;
import cl.duoc.gasvolcan.pedidos.service.CatalogoProductos;
import cl.duoc.gasvolcan.pedidos.service.StockService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stock")
public class StockController {

    private final StockService stock;
    private final CatalogoProductos catalogo;

    public StockController(StockService stock, CatalogoProductos catalogo) {
        this.stock = stock;
        this.catalogo = catalogo;
    }

    @GetMapping
    public List<StockRespuesta> listar() {
        return stock.listar();
    }

    @GetMapping("/{id}")
    public StockRespuesta buscar(@PathVariable String id) {
        return stock.buscar(id);
    }

    @PutMapping("/{id}")
    public StockRespuesta guardar(@PathVariable String id, @Valid @RequestBody StockSolicitud solicitud,
                                  @AuthenticationPrincipal Jwt usuario) {
        stock.validarProductoId(id);
        if (solicitud.version() == null) {
            // Se verifica el producto sin abrir la transacción ni bloquear una fila en Pedidos.
            catalogo.buscar(id, usuario.getTokenValue());
        }
        return stock.guardar(id, solicitud);
    }
}
