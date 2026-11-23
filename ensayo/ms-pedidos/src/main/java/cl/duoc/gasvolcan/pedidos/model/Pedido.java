package cl.duoc.gasvolcan.pedidos.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pedidos", uniqueConstraints = {
    @UniqueConstraint(name = "uq_pedido_referencia_cliente", columnNames = { "correo_cliente", "referencia_compra" })
}, indexes = {
    @Index(name = "idx_pedidos_cliente", columnList = "correo_cliente"),
    @Index(name = "idx_pedidos_repartidor", columnList = "correo_repartidor"),
    @Index(name = "idx_pedidos_fecha", columnList = "fecha_creacion")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "correo_cliente", nullable = false, length = 254)
    private String correoCliente;

    @Column(name = "referencia_compra", length = 36)
    private String referenciaCompra;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 30)
    private String telefono;

    @Column(nullable = false, length = 200)
    private String direccion;

    @Column(nullable = false, length = 80)
    private String comuna;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPedido estado = EstadoPedido.PENDIENTE;

    @Column(name = "correo_repartidor", length = 254)
    private String correoRepartidor;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.PERSIST)
    @OrderBy("id ASC")
    private List<DetallePedido> items = new ArrayList<>();

    // Los precios del catálogo y el total se guardan en pesos chilenos.
    @Column(nullable = false)
    private long total;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    private Instant fechaEntrega;

    @Column(nullable = false)
    private boolean pagoEfectivo;

    private Double latitudEntrega;
    private Double longitudEntrega;
    private Double latitudRepartidor;
    private Double longitudRepartidor;
    private Instant fechaUbicacion;

    public Pedido(String correoCliente, String nombre, String telefono, String direccion, String comuna,
                  Double latitudEntrega, Double longitudEntrega, String referenciaCompra) {
        this.correoCliente = correoCliente;
        this.referenciaCompra = referenciaCompra;
        this.nombre = nombre;
        this.telefono = telefono;
        this.direccion = direccion;
        this.comuna = comuna;
        this.latitudEntrega = latitudEntrega;
        this.longitudEntrega = longitudEntrega;
        // La respuesta inicial y la lectura de MySQL conservan la misma precisión.
        this.fechaCreacion = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    public void agregarItem(String productoId, String nombre, int precio, int cantidad) {
        DetallePedido detalle = new DetallePedido(this, productoId, nombre, precio, cantidad);
        items.add(detalle);
        total += detalle.getSubtotal();
    }

    public void asignar(String correo) {
        correoRepartidor = correo;
        estado = EstadoPedido.ASIGNADO;
    }

    public void iniciarRecorrido() {
        estado = EstadoPedido.EN_CAMINO;
    }

    // Marcar la entrega deja constancia del efectivo recibido por el total del pedido.
    public void registrarEntrega() {
        estado = EstadoPedido.ENTREGADO;
        fechaEntrega = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        pagoEfectivo = true;
    }

    public void actualizarUbicacion(double latitud, double longitud) {
        latitudRepartidor = latitud;
        longitudRepartidor = longitud;
        fechaUbicacion = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
