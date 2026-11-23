package cl.duoc.gasvolcan.negocio.controller;

import cl.duoc.gasvolcan.negocio.dto.ZonaDespachoRespuesta;
import cl.duoc.gasvolcan.negocio.service.ZonaDespachoService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/zonas")
public class ZonaDespachoController {

    private final ZonaDespachoService zonas;

    public ZonaDespachoController(ZonaDespachoService zonas) {
        this.zonas = zonas;
    }

    @GetMapping
    public List<ZonaDespachoRespuesta> listar() {
        return zonas.listar();
    }
}
