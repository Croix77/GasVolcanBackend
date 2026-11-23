package cl.duoc.gasvolcan.negocio.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "categorias")
@Getter
@NoArgsConstructor
public class Categoria {

    // MySQL asigna el identificador de cada categoría nueva.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // El nombre es obligatorio, único y admite hasta 80 caracteres.
    @Setter
    @NotBlank
    @Size(max = 80)
    @Column(nullable = false, unique = true, length = 80)
    private String nombre;
}