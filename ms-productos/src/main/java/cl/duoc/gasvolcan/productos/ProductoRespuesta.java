package cl.duoc.gasvolcan.productos;

public record ProductoRespuesta(
        String id,
        String codigo,
        String nombre,
        String descripcion,
        String descripcionLarga,
        String unidad,
        long categoriaId,
        String categoria,
        int precio,
        Integer precioComercial,
        Integer precioAnterior,
        int stockCritico,
        String imagen,
        boolean oferta,
        Long version
) {
}
