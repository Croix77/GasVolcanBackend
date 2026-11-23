package cl.duoc.gasvolcan.pedidos.dto;

import cl.duoc.gasvolcan.pedidos.model.EstadoPedido;
import jakarta.validation.constraints.NotNull;

public record EstadoPedidoSolicitud(@NotNull EstadoPedido estado) {}
