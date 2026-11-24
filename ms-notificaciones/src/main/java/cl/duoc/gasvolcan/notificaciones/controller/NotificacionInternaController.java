package cl.duoc.gasvolcan.notificaciones.controller;

import cl.duoc.gasvolcan.notificaciones.model.Notificacion;
import cl.duoc.gasvolcan.notificaciones.service.NotificacionService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/interno/notificaciones")
public class NotificacionInternaController {

    private final NotificacionService notificaciones;

    public NotificacionInternaController(NotificacionService notificaciones) {
        this.notificaciones = notificaciones;
    }

    @PostMapping
    ResponseEntity<Notificacion.Vista> crear(@Valid @RequestBody NotificacionService.Solicitud solicitud) {
        NotificacionService.Resultado resultado = notificaciones.enviar(solicitud);
        Notificacion.Vista vista = resultado.vista();
        if (!resultado.creado()) return ResponseEntity.ok(vista);
        return ResponseEntity.created(URI.create("/api/v1/notificaciones/" + vista.id())).body(vista);
    }
}
