package cl.duoc.gasvolcan.pedidos.dto;

import cl.duoc.gasvolcan.pedidos.model.Existencia;

public record StockRespuesta(String id, int stock, long version) {

    public static StockRespuesta de(Existencia existencia) {
        return new StockRespuesta(existencia.getId(), existencia.getStock(), existencia.getVersion());
    }
}
