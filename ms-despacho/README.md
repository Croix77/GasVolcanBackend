# ms-despacho

API del flujo de reparto. Verifica al repartidor activo en Auth, protege las mutaciones con el token del solicitante y envía cambios a Pedidos. El pedido, su estado y el pago se guardan en `ms-pedidos`.

La API pública usa `/api/v1/despacho/`. Operadora o administrador pueden asignar. El repartidor asignado puede registrar ubicación y entrega. El cliente ve estado y mapa desde su historial.

Escucha en el puerto 8085. Configura `AUTH_URL` y `PEDIDOS_URL` para las otras APIs. Variables y ejecución en [Despliegue](../deploy/despliegue-ec2.md).
