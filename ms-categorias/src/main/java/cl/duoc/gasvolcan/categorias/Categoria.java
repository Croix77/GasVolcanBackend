package cl.duoc.gasvolcan.categorias;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categorias")
class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String nombre;

    protected Categoria() {
    }

    Categoria(String nombre) {
        this.nombre = nombre;
    }

    Long getId() {
        return id;
    }

    void cambiarNombre(String nombre) {
        this.nombre = nombre;
    }

    Vista vista() {
        return new Vista(id, nombre);
    }

    record Vista(long id, String nombre) {
    }
}
