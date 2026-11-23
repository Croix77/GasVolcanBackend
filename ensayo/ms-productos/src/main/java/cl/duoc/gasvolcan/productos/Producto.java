package cl.duoc.gasvolcan.productos;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "productos")
class Producto {

    @Id
    private String id;
    private String nombre;
    private Integer precio;

    // La categoría referencia el identificador de otra API.
    private Long categoriaId;

    protected Producto() {
    }

    Producto(String id, String nombre, int precio, long categoriaId) {
        this.id = id;
        this.nombre = nombre;
        this.precio = precio;
        this.categoriaId = categoriaId;
    }

    void cambiarPrecio(int precio) {
        this.precio = precio;
    }

    Vista vista() {
        return new Vista(id, nombre, precio, categoriaId);
    }

    record Vista(String id, String nombre, int precio, long categoriaId) {
    }
}
