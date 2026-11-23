package cl.duoc.gasvolcan.pedidos.exception;

public class CatalogoNoDisponibleException extends RuntimeException {
    public CatalogoNoDisponibleException() {
        super("El catálogo no está disponible. Intenta nuevamente.");
    }
}
