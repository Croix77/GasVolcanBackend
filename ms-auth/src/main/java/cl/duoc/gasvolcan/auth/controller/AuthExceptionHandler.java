package cl.duoc.gasvolcan.auth.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class AuthExceptionHandler {

	@ExceptionHandler(BadCredentialsException.class)
	public ResponseEntity<Map<String, String>> credencialesInvalidas(BadCredentialsException ex) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("mensaje", ex.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> datosInvalidos(MethodArgumentNotValidException ex) {
		Map<String, String> campos = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors()
				.forEach(error -> campos.putIfAbsent(error.getField(), error.getDefaultMessage()));
		return ResponseEntity.badRequest().body(Map.of("mensaje", "Revisa los datos ingresados.", "campos", campos));
	}

	@ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
	public ResponseEntity<Map<String, String>> formatoInvalido() {
		return ResponseEntity.badRequest().body(Map.of("mensaje", "La solicitud tiene un formato inválido."));
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<Map<String, String>> errorSolicitud(ResponseStatusException ex) {
		return ResponseEntity.status(ex.getStatusCode()).body(Map.of("mensaje", ex.getReason()));
	}

	@ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
	public ResponseEntity<Map<String, String>> conflicto() {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(Map.of("mensaje", "Los datos entran en conflicto con un registro existente. Actualiza e intenta nuevamente."));
	}
}
