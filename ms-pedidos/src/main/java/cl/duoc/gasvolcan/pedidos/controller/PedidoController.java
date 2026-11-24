package cl.duoc.gasvolcan.pedidos.controller;

import cl.duoc.gasvolcan.pedidos.dto.AsignacionPedidoSolicitud;
import cl.duoc.gasvolcan.pedidos.dto.EstadoPedidoSolicitud;
import cl.duoc.gasvolcan.pedidos.dto.PedidoRespuesta;
import cl.duoc.gasvolcan.pedidos.dto.PedidoSolicitud;
import cl.duoc.gasvolcan.pedidos.dto.UbicacionPedidoSolicitud;
import cl.duoc.gasvolcan.pedidos.service.PedidoService;
import cl.duoc.gasvolcan.pedidos.service.CompraService;
import cl.duoc.gasvolcan.pedidos.service.NotificacionesClient;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PedidoController {

    private final PedidoService pedidos;
    private final CompraService compras;
    private final NotificacionesClient notificaciones;

    public PedidoController(PedidoService pedidos, CompraService compras, NotificacionesClient notificaciones) {
        this.pedidos = pedidos;
        this.compras = compras;
        this.notificaciones = notificaciones;
    }

    @PostMapping("/pedidos")
    public ResponseEntity<PedidoRespuesta> crear(@Valid @RequestBody PedidoSolicitud solicitud,
                                                JwtAuthenticationToken usuario) {
        try {
            PedidoService.Compra compra = compras.comprar(solicitud, usuario);
            if (!compra.nueva()) return ResponseEntity.ok(compra.pedido());
            notificaciones.avisarPedido(compra.pedido());
            return ResponseEntity.created(URI.create("/api/v1/pedidos/" + compra.pedido().id())).body(compra.pedido());
        } catch (DataIntegrityViolationException conflicto) {
            // Si dos envíos coinciden, la transacción perdedora ya se revirtió; se consulta la confirmada.
            return pedidos.buscarCompra(solicitud.referenciaCompra(), usuario)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> conflicto);
        }
    }

    @GetMapping("/pedidos")
    public List<PedidoRespuesta> listar(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
        Authentication usuario
    ) {
        return pedidos.listar(fecha, usuario);
    }

    @GetMapping("/pedidos/{id}")
    public PedidoRespuesta buscar(@PathVariable Long id, Authentication usuario) {
        return pedidos.buscar(id, usuario);
    }

    @PatchMapping("/interno/pedidos/{id}/asignacion")
    public PedidoRespuesta asignar(@PathVariable Long id, @Valid @RequestBody AsignacionPedidoSolicitud solicitud) {
        return pedidos.asignar(id, solicitud.correoRepartidor());
    }

    @PatchMapping("/interno/pedidos/{id}/estado")
    public PedidoRespuesta cambiarEstado(@PathVariable Long id, @Valid @RequestBody EstadoPedidoSolicitud solicitud,
                                         Authentication usuario) {
        return pedidos.cambiarEstado(id, solicitud.estado(), usuario);
    }

    @PutMapping("/interno/pedidos/{id}/ubicacion")
    public PedidoRespuesta ubicar(@PathVariable Long id, @Valid @RequestBody UbicacionPedidoSolicitud solicitud,
                                 Authentication usuario) {
        return pedidos.ubicar(id, solicitud.latitud(), solicitud.longitud(), usuario);
    }
}
