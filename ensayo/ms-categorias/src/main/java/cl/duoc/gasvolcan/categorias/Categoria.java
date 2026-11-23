package cl.duoc.gasvolcan.categorias;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categorias")
class Categoria {

    @Id
    private Long id;
    private String nombre;

    protected Categoria() {
    }

    Categoria(long id, String nombre) {
        this.id = id;
        this.nombre = nombre;
    }

    Vista vista() {
        return new Vista(id, nombre);
    }

    record Vista(long id, String nombre) {
    }
}
