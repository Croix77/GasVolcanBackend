package cl.duoc.gasvolcan.productos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ProductoSolicitud(
        @NotBlank(message = "El código es obligatorio.")
        @Pattern(regexp = "[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*", message = "El código admite letras, números y guiones entre grupos.")
        @Size(max = 40, message = "El código admite hasta 40 caracteres.")
        String codigo,
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 100, message = "El nombre admite hasta 100 caracteres.")
        String nombre,
        @NotBlank(message = "La descripción es obligatoria.")
        @Size(max = 400, message = "La descripción admite hasta 400 caracteres.")
        String descripcion,
        @Size(max = 1000, message = "La descripción larga admite hasta 1000 caracteres.")
        String descripcionLarga,
        @NotBlank(message = "La unidad es obligatoria.")
        @Size(max = 30, message = "La unidad admite hasta 30 caracteres.")
        String unidad,
        @NotNull(message = "La categoría es obligatoria.")
        @Positive(message = "El identificador de categoría debe ser positivo.")
        Long categoriaId,
        @NotNull(message = "El precio es obligatorio.")
        @PositiveOrZero(message = "El precio debe ser mayor o igual a cero.")
        Integer precio,
        @PositiveOrZero(message = "El precio comercial debe ser mayor o igual a cero.")
        Integer precioComercial,
        @PositiveOrZero(message = "El precio anterior debe ser mayor o igual a cero.")
        Integer precioAnterior,
        @PositiveOrZero(message = "El stock crítico debe ser mayor o igual a cero.")
        Integer stockCritico,
        @Size(max = 255, message = "La ruta de la imagen admite hasta 255 caracteres.")
        String imagen,
        Boolean oferta,
        @PositiveOrZero(message = "La versión debe ser mayor o igual a cero.")
        Long version) {
}
