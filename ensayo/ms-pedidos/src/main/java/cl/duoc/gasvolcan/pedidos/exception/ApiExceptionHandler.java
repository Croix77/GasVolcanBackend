package cl.duoc.gasvolcan.pedidos.exception;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> datosInvalidos(MethodArgumentNotValidException error) {
        var campo = error.getBindingResult().getFieldError();
        String mensaje = campo == null ? "Los datos son inválidos." : campo.getDefaultMessage();
        return ResponseEntity.badRequest().body(Map.of("error", mensaje));
    }

    @ExceptionHandler(CatalogoNoDisponibleException.class)
    public ResponseEntity<Map<String, String>> catalogoNoDisponible(CatalogoNoDisponibleException error) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", error.getMessage()));
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<Map<String, String>> recursoNoEncontrado(RecursoNoEncontradoException error) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", error.getMessage()));
    }

    @ExceptionHandler(NegocioInvalidoException.class)
    public ResponseEntity<Map<String, String>> negocioInvalido(NegocioInvalidoException error) {
        return ResponseEntity.badRequest().body(Map.of("error", error.getMessage()));
    }

    @ExceptionHandler(ConflictoNegocioException.class)
    public ResponseEntity<Map<String, String>> conflicto(ConflictoNegocioException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", error.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> jsonInvalido() {
        return ResponseEntity.badRequest().body(Map.of("error", "Envía un cuerpo JSON válido."));
    }

    // La base mantiene la unicidad incluso si llegan dos solicitudes a la vez.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> restriccionDeDatos() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "La operación entra en conflicto con los datos registrados."));
    }
}
