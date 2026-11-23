package cl.duoc.gasvolcan.pedidos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record PedidoSolicitud(
    @NotBlank @Size(max = 100) @Pattern(regexp = "[\\p{L}\\p{M} .’'\\-]+",
        message = "Ingresa un nombre válido.") String nombre,
    @NotBlank @Size(max = 30) String telefono,
    @NotBlank @Size(max = 200) String direccion,
    @NotBlank @Size(max = 80) String comuna,
    @NotEmpty(message = "Agrega al menos un producto al pedido.")
        List<@NotNull @Valid ItemSolicitud> items,
    @NotNull(message = "Marca el punto de entrega.") @DecimalMin("-90") @DecimalMax("90") Double latitudEntrega,
    @NotNull(message = "Marca el punto de entrega.") @DecimalMin("-180") @DecimalMax("180") Double longitudEntrega,
    UUID referenciaCompra
) {
    public record ItemSolicitud(
        @NotBlank @Size(max = 40) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*",
            message = "El identificador del producto no es válido.") String id,
        @NotNull @Min(1) @Max(3) Integer cantidad,
        @PositiveOrZero Integer precioEsperado
    ) {}
}
