package cl.duoc.gasvolcan.contacto.model;

import cl.duoc.gasvolcan.contacto.service.ContactoService;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "mensajes_contacto",
        uniqueConstraints = @UniqueConstraint(name = "uq_contacto_correo_referencia", columnNames = {"correo", "referencia"}),
        indexes = @Index(name = "idx_contacto_fecha_id", columnList = "fecha_creacion,id"))
public class MensajeContacto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100, nullable = false)
    private String nombre;

    @Column(length = 254, nullable = false)
    private String correo;

    @Column(length = 1000, nullable = false)
    private String mensaje;

    @Column(length = 36, nullable = false)
    private String referencia;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    protected MensajeContacto() {
    }

    public MensajeContacto(String nombre, String correo, String mensaje, String referencia) {
        this.nombre = nombre;
        this.correo = correo;
        this.mensaje = mensaje;
        this.referencia = referencia;
        this.fechaCreacion = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    public ContactoService.Recepcion recepcion() {
        return new ContactoService.Recepcion(id, fechaCreacion);
    }

    public ContactoService.Mensaje datoBandeja() {
        return new ContactoService.Mensaje(id, nombre, correo, mensaje, fechaCreacion);
    }
}
