package cl.duoc.gasvolcan.productos.model;

import cl.duoc.gasvolcan.productos.dto.ProductoRespuesta;
import cl.duoc.gasvolcan.productos.dto.ProductoSolicitud;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "productos")
public class Producto {

    // El identificador se conserva aunque se edite el código comercial.
    @Id
    @Column(length = 40)
    private String id;

    @Version
    private Long version;

    @Column(nullable = false, unique = true, length = 40)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 400)
    private String descripcion;

    @Column(length = 1000)
    private String descripcionLarga;

    @Column(nullable = false, length = 30)
    private String unidad;

    @Column(nullable = false)
    private long categoriaId;

    @Column(nullable = false, length = 64)
    private String referenciaCategoria;

    @Column(nullable = false)
    private int precio;

    private Integer precioComercial;
    private Integer precioAnterior;

    @Column(nullable = false)
    private int stockCritico;

    @Column(length = 255)
    private String imagen;

    @Column(nullable = false)
    private boolean oferta;

    @Column(nullable = false)
    private boolean activo = true;

    protected Producto() {
    }

    public Producto(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public long getCategoriaId() {
        return categoriaId;
    }

    public String getReferenciaCategoria() {
        return referenciaCategoria;
    }

    public Integer getPrecioAnterior() {
        return precioAnterior;
    }

    public boolean isActivo() {
        return activo;
    }

    public void cambiarCategoria(long id, String referencia) {
        this.categoriaId = id;
        this.referenciaCategoria = referencia;
    }

    public void actualizar(ProductoSolicitud datos, String codigo) {
        this.codigo = codigo;
        nombre = datos.nombre().strip();
        descripcion = datos.descripcion().strip();
        descripcionLarga = datos.descripcionLarga() == null ? null : datos.descripcionLarga().strip();
        unidad = datos.unidad().strip();
        precio = datos.precio();
        precioComercial = datos.precioComercial();
        oferta = Boolean.TRUE.equals(datos.oferta());
        precioAnterior = oferta ? datos.precioAnterior() : null;
        stockCritico = datos.stockCritico() == null ? 10 : datos.stockCritico();
        imagen = datos.imagen() == null ? "" : datos.imagen().strip();
    }

    public void cambiarPrecio(int precio) {
        this.precio = precio;
    }

    public void desactivar() {
        activo = false;
    }

    public ProductoRespuesta vista(String categoria) {
        return new ProductoRespuesta(id, codigo, nombre, descripcion, descripcionLarga, unidad, categoriaId,
                categoria, precio, precioComercial, precioAnterior, stockCritico, imagen, oferta, version);
    }

    public PrecioVenta precioVenta() {
        return new PrecioVenta(id, nombre, precio);
    }

    public record PrecioVenta(String id, String nombre, int precio) {
    }
}
