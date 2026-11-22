package cl.duoc.gasvolcan.negocio.exception;

public class CategoriaDuplicadaException extends RuntimeException {

    public CategoriaDuplicadaException() {
        super("La categoría ya existe.");
    }
}
