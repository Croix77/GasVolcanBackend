package cl.duoc.gasvolcan.pedidos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record StockSolicitud(
        @NotNull(message = "Indica el stock.")
        @PositiveOrZero(message = "El stock no puede ser negativo.") Integer stock,
        @PositiveOrZero(message = "La versión de stock no es válida.") Long version) {}
