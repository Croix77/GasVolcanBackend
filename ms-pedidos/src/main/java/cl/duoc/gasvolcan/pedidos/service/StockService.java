package cl.duoc.gasvolcan.pedidos.service;

import cl.duoc.gasvolcan.pedidos.dto.StockRespuesta;
import cl.duoc.gasvolcan.pedidos.dto.StockSolicitud;
import cl.duoc.gasvolcan.pedidos.exception.ConflictoNegocioException;
import cl.duoc.gasvolcan.pedidos.exception.NegocioInvalidoException;
import cl.duoc.gasvolcan.pedidos.exception.RecursoNoEncontradoException;
import cl.duoc.gasvolcan.pedidos.model.Existencia;
import cl.duoc.gasvolcan.pedidos.repository.ExistenciaRepository;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockService {

    private static final Pattern ID = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private final ExistenciaRepository existencias;

    public StockService(ExistenciaRepository existencias) {
        this.existencias = existencias;
    }

    @Transactional(readOnly = true)
    public List<StockRespuesta> listar() {
        return existencias.findAll(Sort.by("id")).stream().map(StockRespuesta::de).toList();
    }

    @Transactional(readOnly = true)
    public StockRespuesta buscar(String id) {
        return existencias.findById(id)
                .map(StockRespuesta::de)
                .orElseThrow(() -> new RecursoNoEncontradoException("Stock no registrado: " + id));
    }

    @Transactional
    public StockRespuesta guardar(String id, StockSolicitud solicitud) {
        if (solicitud.version() == null) {
            // Para el alta, la clave primaria resuelve dos intentos simultáneos sin sobrescribir stock.
            if (existencias.existsById(id)) throw stockCambio();
            return StockRespuesta.de(existencias.saveAndFlush(new Existencia(id, solicitud.stock())));
        }

        Existencia existencia = existencias.buscarParaActualizar(id).orElseThrow(this::stockCambio);
        if (!solicitud.version().equals(existencia.getVersion())) throw stockCambio();

        existencia.ajustar(solicitud.stock());
        existencias.flush();
        return StockRespuesta.de(existencia);
    }

    public void validarProductoId(String id) {
        if (id.length() > 40 || !ID.matcher(id).matches()) {
            throw new NegocioInvalidoException("El identificador del producto no es válido.");
        }
    }

    private ConflictoNegocioException stockCambio() {
        return new ConflictoNegocioException("El stock cambió mientras editabas. Consulta las existencias de nuevo.");
    }
}
