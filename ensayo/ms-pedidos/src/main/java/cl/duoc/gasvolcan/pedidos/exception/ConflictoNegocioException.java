package cl.duoc.gasvolcan.pedidos.exception;

public class ConflictoNegocioException extends RuntimeException {

    public ConflictoNegocioException(String mensaje) {
        super(mensaje);
    }
}
