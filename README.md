# Gas El Volcán · Backend

Gas El Volcán usa cinco microservicios Spring Boot. Cada servicio inicia por separado. React consume las rutas públicas con un JWT emitido por Auth.

| Servicio | Responsabilidad | Puerto local |
| --- | --- | --- |
| [ms-auth](https://github.com/Croix77/GasVolcanAuth/tree/main/ms-auth) | Cuentas, login, renovación del JWT y roles | 8081 |
| [ms-categorias](ms-categorias/) | Categorías y control de referencias del catálogo | 8082 |
| [ms-productos](ms-productos/) | Fichas, precios y ofertas | 8083 |
| [ms-pedidos](ms-pedidos/) | Carrito confirmado, historial, existencias y pago en efectivo | 8084 |
| [ms-despacho](ms-despacho/) | Asignación y ubicación de repartidores, estado de entrega | 8085 |

[Frontend React](https://github.com/Croix77/GasvolcanReact) · [Configurar entornos y EC2](deploy/despliegue-ec2.md) · [Migrar los datos de ms-negocio](deploy/migracion.md)

## Cómo funciona la compra

React consulta el precio en Productos y el stock en Pedidos. Envía los ítems con el precio mostrado y una referencia UUID. Pedidos verifica el precio vigente por REST y, en una transacción MySQL, guarda el pedido y descuenta las existencias. Devuelve la compra anterior si la confirmación se reintenta con la misma referencia. El pedido conserva el total y las líneas de la compra al momento de confirmarla.

Despacho consulta a Auth antes de asignar un repartidor. Envía los cambios de estado por una ruta interna de Pedidos. La entrega registra el pago en efectivo dentro del mismo pedido.

## Verificar

Ejecuta Java 17 y Maven Wrapper desde cada carpeta. En Windows:

```powershell
.\mvnw.cmd verify
```

[Prueba integrada](deploy/verificar-integracion.ps1) levanta los cinco procesos y usa bases MySQL aisladas. Verifica roles, CRUD, precios, la última unidad, reintentos, caída de catálogo y despacho. El parámetro opcional `-LegacyJar` prueba la migración desde el JAR de `ms-negocio`. Mantén los archivos de entorno reales y los artefactos de compilación fuera de Git.
