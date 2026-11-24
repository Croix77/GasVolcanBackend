package cl.duoc.gasvolcan.pedidos.exception;

public class NegocioInvalidoException extends RuntimeException {

    public NegocioInvalidoException(String mensaje) {
        super(mensaje);
    }
}
