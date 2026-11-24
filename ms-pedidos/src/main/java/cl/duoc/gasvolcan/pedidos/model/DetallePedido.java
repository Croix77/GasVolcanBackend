package cl.duoc.gasvolcan.pedidos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "detalles_pedido")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DetallePedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    // Se conserva la compra original aunque cambien el nombre o el precio en el catálogo.
    @Column(nullable = false, length = 40)
    private String productoId;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false)
    private int precioUnitario;

    @Column(nullable = false)
    private int cantidad;

    public DetallePedido(Pedido pedido, String productoId, String nombre, int precioUnitario, int cantidad) {
        this.pedido = pedido;
        this.productoId = productoId;
        this.nombre = nombre;
        this.precioUnitario = precioUnitario;
        this.cantidad = cantidad;
    }

    public long getSubtotal() {
        return (long) precioUnitario * cantidad;
    }
}
