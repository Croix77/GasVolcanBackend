package cl.duoc.gasvolcan.negocio.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "productos")
@Getter
@Setter
@NoArgsConstructor
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Column(nullable = false)
    private Integer precio;

    private Integer precioComercial;

    private Integer precioAnterior;

    @Column(nullable = false)
    private Integer stock;

    @Column(nullable = false)
    private Integer stockCritico;

    @Column(length = 255)
    private String imagen;

    @Column(nullable = false)
    private boolean oferta;

    @Column(nullable = false)
    private boolean activo = true;
}
