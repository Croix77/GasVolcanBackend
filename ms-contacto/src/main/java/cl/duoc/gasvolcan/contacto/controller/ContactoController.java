package cl.duoc.gasvolcan.contacto.controller;

import cl.duoc.gasvolcan.contacto.dto.MensajeSolicitud;
import cl.duoc.gasvolcan.contacto.service.ContactoService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/contacto/mensajes")
public class ContactoController {

    private final ContactoService contacto;

    public ContactoController(ContactoService contacto) {
        this.contacto = contacto;
    }

    @PostMapping
    public ResponseEntity<ContactoService.Recepcion> enviar(@Valid @RequestBody MensajeSolicitud solicitud,
            @AuthenticationPrincipal Jwt sesion) {
        var envio = contacto.recibir(solicitud, sesion.getSubject());
        return ResponseEntity.status(envio.nuevo() ? HttpStatus.CREATED : HttpStatus.OK).body(envio.recepcion());
    }

    @GetMapping
    public ContactoService.Bandeja listar(@RequestParam(defaultValue = "0") int pagina) {
        return contacto.listar(pagina);
    }
}
