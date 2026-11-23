package cl.duoc.gasvolcan.pedidos.service;

import cl.duoc.gasvolcan.pedidos.dto.PedidoSolicitud;
import cl.duoc.gasvolcan.pedidos.exception.ConflictoNegocioException;
import cl.duoc.gasvolcan.pedidos.exception.NegocioInvalidoException;
import cl.duoc.gasvolcan.pedidos.service.CatalogoProductos.ProductoCatalogo;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class CompraService {

    private static final int MAX_POR_PRODUCTO = 3;

    private final CatalogoProductos catalogo;
    private final PedidoService pedidos;

    public CompraService(CatalogoProductos catalogo, PedidoService pedidos) {
        this.catalogo = catalogo;
        this.pedidos = pedidos;
    }

    public PedidoService.Compra comprar(PedidoSolicitud solicitud, JwtAuthenticationToken sesion) {
        // Un reintento confirmado puede responder incluso si el catálogo está caído.
        var anterior = pedidos.buscarCompra(solicitud.referenciaCompra(), sesion);
        if (anterior.isPresent()) {
            return new PedidoService.Compra(anterior.get(), false);
        }

        Map<String, Integer> cantidades = new TreeMap<>();
        for (PedidoSolicitud.ItemSolicitud item : solicitud.items()) {
            int cantidad = cantidades.merge(item.id(), item.cantidad(), Integer::sum);
            if (cantidad > MAX_POR_PRODUCTO) {
                throw new NegocioInvalidoException("Puedes pedir hasta 3 unidades por producto.");
            }
        }

        // La red se consulta antes de abrir la transacción o bloquear existencias.
        Map<String, ProductoCatalogo> productos = new TreeMap<>();
        for (String id : cantidades.keySet()) {
            productos.put(id, catalogo.buscar(id, sesion.getToken().getTokenValue()));
        }
        for (PedidoSolicitud.ItemSolicitud item : solicitud.items()) {
            if (item.precioEsperado() != null && !item.precioEsperado().equals(productos.get(item.id()).precio())) {
                throw new ConflictoNegocioException("Cambió el precio de " + productos.get(item.id()).nombre()
                        + ". Revisa tu carrito antes de comprar.");
            }
        }

        return pedidos.crear(solicitud, sesion, cantidades, productos);
    }
}
