package cl.duoc.gasvolcan.negocio.dto;

import cl.duoc.gasvolcan.negocio.model.EstadoPedido;
import jakarta.validation.constraints.NotNull;

public record EstadoPedidoSolicitud(@NotNull EstadoPedido estado) {}
