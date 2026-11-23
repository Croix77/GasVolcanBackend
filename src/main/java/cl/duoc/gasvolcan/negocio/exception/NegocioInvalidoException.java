package cl.duoc.gasvolcan.negocio.exception;

public class NegocioInvalidoException extends RuntimeException {

    public NegocioInvalidoException(String mensaje) {
        super(mensaje);
    }
}
