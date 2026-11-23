package cl.duoc.gasvolcan.pedidos.dto;

import cl.duoc.gasvolcan.pedidos.model.EstadoPedido;
import cl.duoc.gasvolcan.pedidos.model.Pedido;
import java.time.Instant;
import java.util.List;

public record PedidoRespuesta(
    Long id,
    String correoCliente,
    String nombre,
    String telefono,
    String direccion,
    String comuna,
    EstadoPedido estado,
    String correoRepartidor,
    List<ItemRespuesta> items,
    long total,
    Instant fechaCreacion,
    Instant fechaEntrega,
    boolean pagoEfectivo,
    Double latitudEntrega,
    Double longitudEntrega,
    Double latitudRepartidor,
    Double longitudRepartidor,
    Instant fechaUbicacion
) {
    public record ItemRespuesta(String id, String nombre, int precioUnitario, int cantidad, long subtotal) {}

    public static PedidoRespuesta desde(Pedido pedido) {
        List<ItemRespuesta> items = pedido.getItems().stream()
            .map(item -> new ItemRespuesta(
                item.getProductoId(), item.getNombre(), item.getPrecioUnitario(),
                item.getCantidad(), item.getSubtotal()))
            .toList();

        return new PedidoRespuesta(pedido.getId(), pedido.getCorreoCliente(), pedido.getNombre(),
            pedido.getTelefono(), pedido.getDireccion(), pedido.getComuna(), pedido.getEstado(),
            pedido.getCorreoRepartidor(), items, pedido.getTotal(), pedido.getFechaCreacion(),
            pedido.getFechaEntrega(), pedido.isPagoEfectivo(), pedido.getLatitudEntrega(),
            pedido.getLongitudEntrega(), pedido.getLatitudRepartidor(), pedido.getLongitudRepartidor(),
            pedido.getFechaUbicacion());
    }
}
