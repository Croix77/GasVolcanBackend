package cl.duoc.gasvolcan.negocio.dto;

import cl.duoc.gasvolcan.negocio.model.Producto;

public record ProductoRespuesta(
        String id,
        String codigo,
        String nombre,
        String descripcion,
        String descripcionLarga,
        String unidad,
        Long categoriaId,
        String categoria,
        Integer precio,
        Integer precioComercial,
        Integer precioAnterior,
        Integer stock,
        Integer stockCritico,
        String imagen,
        boolean oferta,
        Long version) {

    // La API entrega el nombre y el identificador de la categoría en el mismo JSON.
    public static ProductoRespuesta desde(Producto producto) {
        return new ProductoRespuesta(
                producto.getId(),
                producto.getCodigo(),
                producto.getNombre(),
                producto.getDescripcion(),
                producto.getDescripcionLarga(),
                producto.getUnidad(),
                producto.getCategoria().getId(),
                producto.getCategoria().getNombre(),
                producto.getPrecio(),
                producto.getPrecioComercial(),
                producto.getPrecioAnterior(),
                producto.getStock(),
                producto.getStockCritico(),
                producto.getImagen(),
                producto.isOferta(),
                producto.getVersion());
    }
}
