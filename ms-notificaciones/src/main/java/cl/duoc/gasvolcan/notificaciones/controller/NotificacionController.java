package cl.duoc.gasvolcan.notificaciones.controller;

import cl.duoc.gasvolcan.notificaciones.model.Notificacion;
import cl.duoc.gasvolcan.notificaciones.service.NotificacionService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notificaciones")
public class NotificacionController {

    private final NotificacionService notificaciones;

    public NotificacionController(NotificacionService notificaciones) {
        this.notificaciones = notificaciones;
    }

    @GetMapping
    List<Notificacion.Vista> listar() {
        return notificaciones.listar();
    }

    @GetMapping("/{id}")
    Notificacion.Vista buscar(@PathVariable long id) {
        return notificaciones.buscar(id);
    }

    @PostMapping
    ResponseEntity<Notificacion.Vista> crear(@Valid @RequestBody NotificacionService.Solicitud solicitud) {
        NotificacionService.Resultado resultado = notificaciones.enviar(solicitud);
        Notificacion.Vista vista = resultado.vista();
        if (!resultado.creado()) return ResponseEntity.ok(vista);
        return ResponseEntity.created(URI.create("/api/v1/notificaciones/" + vista.id())).body(vista);
    }
}
