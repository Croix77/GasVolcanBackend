package cl.duoc.gasvolcan.pedidos.controller;

import cl.duoc.gasvolcan.pedidos.dto.ZonaDespachoRespuesta;
import cl.duoc.gasvolcan.pedidos.service.ZonaDespachoService;
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
