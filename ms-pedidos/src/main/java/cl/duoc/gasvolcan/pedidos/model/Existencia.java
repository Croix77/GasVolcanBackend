package cl.duoc.gasvolcan.pedidos.model;

import cl.duoc.gasvolcan.pedidos.exception.ConflictoNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "existencias")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Existencia {

    @Id
    @Column(length = 40)
    private String id;

    @Column(nullable = false)
    private int stock;

    @Version
    @Column(nullable = false)
    private Long version;

    public Existencia(String id, int stock) {
        this.id = id;
        this.stock = stock;
    }

    public void ajustar(int stock) {
        this.stock = stock;
    }

    // Se llama con la fila bloqueada y dentro de la transacción del pedido.
    public void descontar(int cantidad, String nombre) {
        if (cantidad > stock) {
            throw new ConflictoNegocioException("Stock insuficiente para " + nombre + ".");
        }
        stock -= cantidad;
    }
}
