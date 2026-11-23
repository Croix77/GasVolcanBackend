package cl.duoc.gasvolcan.pedidos.controller;

import cl.duoc.gasvolcan.pedidos.exception.RecursoNoEncontradoException;
import cl.duoc.gasvolcan.pedidos.model.Existencia;
import cl.duoc.gasvolcan.pedidos.repository.ExistenciaRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stock")
public class StockController {

    private final ExistenciaRepository existencias;

    public StockController(ExistenciaRepository existencias) {
        this.existencias = existencias;
    }

    @GetMapping("/{id}")
    public StockRespuesta buscar(@PathVariable String id) {
        Existencia existencia = existencias.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Stock no registrado: " + id));
        return new StockRespuesta(existencia.getId(), existencia.getStock());
    }

    public record StockRespuesta(String id, int stock) {}
}
