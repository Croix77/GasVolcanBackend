package cl.duoc.gasvolcan.categorias.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "categoria_productos")
public class CategoriaProducto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Column(nullable = false, length = 40)
    private String productoId;

    @Column(nullable = false, unique = true, length = 64)
    private String referencia;

    protected CategoriaProducto() {
    }

    public CategoriaProducto(Categoria categoria, String productoId, String referencia) {
        this.categoria = categoria;
        this.productoId = productoId;
        this.referencia = referencia;
    }

    public boolean coincide(Categoria categoria, String productoId) {
        return this.categoria.getId().equals(categoria.getId()) && this.productoId.equals(productoId);
    }
}
