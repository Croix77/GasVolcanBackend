package cl.duoc.gasvolcan.despacho.controller;

import cl.duoc.gasvolcan.despacho.service.DespachoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/despacho")
public class DespachoController {
    private final DespachoService despacho;

    public DespachoController(DespachoService despacho) {
        this.despacho = despacho;
    }

    @GetMapping
    public ResponseEntity<byte[]> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @AuthenticationPrincipal Jwt usuario) {
        String ruta = fecha == null ? "" : "?fecha=" + fecha;
        return despacho.pedido(HttpMethod.GET, ruta, null, usuario);
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> buscar(@PathVariable long id, @AuthenticationPrincipal Jwt usuario) {
        return despacho.pedido(HttpMethod.GET, "/" + id, null, usuario);
    }

    @PatchMapping("/{id}/asignacion")
    public ResponseEntity<byte[]> asignar(
            @PathVariable long id, @Valid @RequestBody Asignacion solicitud, @AuthenticationPrincipal Jwt usuario) {
        despacho.validarRepartidor(solicitud.correoRepartidor(), usuario);
        return despacho.pedido(HttpMethod.PATCH, "/" + id + "/asignacion", solicitud, usuario);
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<byte[]> cambiarEstado(
            @PathVariable long id, @Valid @RequestBody CambioEstado solicitud, @AuthenticationPrincipal Jwt usuario) {
        return despacho.pedido(HttpMethod.PATCH, "/" + id + "/estado", solicitud, usuario);
    }

    @PutMapping("/{id}/ubicacion")
    public ResponseEntity<byte[]> ubicar(
            @PathVariable long id, @Valid @RequestBody Ubicacion solicitud, @AuthenticationPrincipal Jwt usuario) {
        return despacho.pedido(HttpMethod.PUT, "/" + id + "/ubicacion", solicitud, usuario);
    }

    public record Asignacion(@NotBlank @Email @Size(max = 254) String correoRepartidor) {}

    public record CambioEstado(@NotNull Estado estado) {}

    public enum Estado { PENDIENTE, ASIGNADO, EN_CAMINO, ENTREGADO }

    public record Ubicacion(
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitud,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitud) {}
}
