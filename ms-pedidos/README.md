# ms-pedidos

API de compras, inventario e historial. El pedido y el descuento de existencias comparten una transacción en `gas_volcan_pedidos`. El servicio consulta el precio que devuelve Productos antes de abrir la transacción y guarda una copia en el pedido.

- `POST /api/v1/pedidos` recibe los artículos con `id`, `cantidad` y `precioEsperado`, el punto de entrega y una `referenciaCompra` UUID. Precio cambiado o stock insuficiente devuelven `409`. La referencia evita descuentos duplicados.
- `GET /api/v1/pedidos` y `GET /api/v1/pedidos/{id}` respetan el rol y el dueño del pedido.
- `GET /api/v1/stock` y `GET /api/v1/stock/{id}` exponen la cantidad y versión. ADMIN usa `PUT /api/v1/stock/{id}` para dar de alta o ajustar existencias. Si otra persona compra o ajusta entre la lectura y la edición se devuelve `409`.
- Las mutaciones de reparto están en `/api/v1/interno/pedidos/`. Se consumen desde Despacho por la red interna.

Escucha en el puerto 8084. Usa `PRODUCTOS_URL` para la API de Productos. Con `APP_SEED_STOCK=true` carga las existencias iniciales del caso. Variables y ejecución en [Despliegue](../deploy/despliegue-ec2.md).
