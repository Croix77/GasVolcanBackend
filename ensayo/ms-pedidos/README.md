# ms-pedidos

Este proyecto Spring Boot del ensayo reúne pedidos y existencias en un esquema MySQL propio. Obtiene el nombre y precio del producto de `ms-productos` por HTTP, utilizando el token de acceso de la compra. Consulta el catálogo antes de abrir la transacción de escritura.

Dentro de la transacción bloquea las existencias por identificador en un orden fijo. Si el carrito está completo, guarda el pedido y resta las unidades. Si falta un producto, se revierte todo. El pedido guarda las unidades y el precio de cada línea al comprar.

## Contrato del ensayo

- `POST /api/v1/pedidos` crea la compra con un JWT de cliente. En cada ítem puede llegar `precioEsperado`. Si se informó y cambió, devuelve `409` sin reservar stock.
- `referenciaCompra` identifica un intento ya confirmado. Si llega de nuevo para el mismo cliente, devuelve el pedido original con `200` y evita una llamada nueva al catálogo.
- `GET /api/v1/stock/{id}` consulta unidades. Con `APP_SEED_STOCK=true`, la base de ensayo arranca con una unidad de `gas-5kg` y cuatro de `gas-15kg`. Las existencias permanecen al reiniciar con la misma base.
- Las transiciones de asignación, ubicación y entrega verifican el rol y el repartidor asignado. La entrega confirma el efectivo de la compra.
- Si `ms-productos` está caído, una compra nueva recibe `503` y su stock queda intacto.

## Ejecutar

Configura `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `PORT` y `PRODUCTOS_URL`. Esta última variable apunta a la raíz del servicio de catálogo, que expone `GET /api/v1/productos/{id}`.

```powershell
.\mvnw.cmd verify
```
