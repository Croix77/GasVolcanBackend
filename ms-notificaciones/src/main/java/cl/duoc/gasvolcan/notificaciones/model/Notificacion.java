package cl.duoc.gasvolcan.notificaciones.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "notificaciones", indexes = @Index(name = "idx_notificaciones_referencia", columnList = "referencia"))
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Canal canal;

    @Column(nullable = false, length = 254)
    private String destino;

    @Column(length = 120)
    private String asunto;

    @Column(nullable = false, length = 2000)
    private String mensaje;

    @Column(length = 100)
    private String referencia;

    @Column(length = 120, unique = true)
    private String idempotencia;

    @Column(nullable = false, length = 12)
    private String estado = "PENDIENTE";

    @Column(length = 20)
    private String proveedor;

    @Column(length = 300)
    private String detalle;

    @Column(nullable = false)
    private Instant fechaCreacion = Instant.now();

    private Instant fechaEnvio;

    protected Notificacion() {
    }

    public Notificacion(Canal canal, String destino, String asunto, String mensaje, String referencia,
                        String idempotencia) {
        this.canal = canal;
        this.destino = destino;
        this.asunto = asunto;
        this.mensaje = mensaje;
        this.referencia = referencia;
        this.idempotencia = idempotencia;
    }

    public Canal getCanal() {
        return canal;
    }

    public String getDestino() {
        return destino;
    }

    public String getAsunto() {
        return asunto;
    }

    public String getMensaje() {
        return mensaje;
    }

    public String getReferencia() {
        return referencia;
    }

    public String getIdempotencia() {
        return idempotencia;
    }

    public void enviada(String proveedor, String detalle) {
        this.estado = "ENVIADO";
        this.proveedor = proveedor;
        this.detalle = detalle;
        this.fechaEnvio = Instant.now();
    }

    public void simulada(String detalle) {
        this.estado = "SIMULADO";
        this.proveedor = "SIMULADO";
        this.detalle = detalle;
        this.fechaEnvio = Instant.now();
    }

    public void fallida(String motivo) {
        this.estado = "FALLIDO";
        this.detalle = motivo.length() > 300 ? motivo.substring(0, 300) : motivo;
        this.fechaEnvio = Instant.now();
    }

    public Vista vista() {
        return new Vista(id, canal, destino, asunto, mensaje, referencia, idempotencia, estado, proveedor,
                "SIMULADO".equals(estado), detalle, fechaCreacion, fechaEnvio);
    }

    public record Vista(long id, Canal canal, String destino, String asunto, String mensaje, String referencia,
                        String idempotencia, String estado, String proveedor, boolean simulado, String detalle,
                        Instant fechaCreacion, Instant fechaEnvio) {
    }
}
