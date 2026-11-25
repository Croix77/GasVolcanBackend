# Gas El Volcán · Backend

Este repositorio contiene seis microservicios Spring Boot de Gas El Volcán. Ejecuta cada servicio desde su propia carpeta. React consume las rutas públicas con un JWT emitido por Auth.

| Servicio | Responsabilidad | Puerto local |
| --- | --- | --- |
| [ms-auth](ms-auth/) | Cuentas, login, renovación del JWT y roles | 8081 |
| [ms-categorias](ms-categorias/) | Categorías y control de referencias del catálogo | 8082 |
| [ms-productos](ms-productos/) | Fichas, precios y ofertas | 8083 |
| [ms-pedidos](ms-pedidos/) | Carrito confirmado, historial, existencias y pago en efectivo | 8084 |
| [ms-despacho](ms-despacho/) | Asignación y ubicación de repartidores, estado de entrega | 8085 |
| [ms-contacto](ms-contacto/) | Consultas de clientes y bandeja administrativa | 8086 |

[Frontend React](https://github.com/Croix77/GasvolcanReact) · [Configurar entornos y EC2](deploy/despliegue-ec2.md) · [Migrar los datos de ms-negocio](deploy/migracion.md)

## Organización del código Java

Cada servicio conserva su clase de arranque en el paquete principal. Las clases se agrupan por responsabilidad.

- controller recibe las solicitudes HTTP.
- service aplica las reglas del negocio.
- repository accede a la base de datos.
- model representa las entidades guardadas.
- dto define datos de entrada y salida cuando se declaran como clases propias.
- config y security configuran Spring, permisos y JWT.
- exception convierte errores en respuestas de la API.
- client realiza llamadas REST a otros servicios.

Cada módulo contiene los paquetes que necesita. Las pruebas HTTP se encuentran en src/test/java.

## Cómo funciona la compra

React consulta el precio en Productos y el stock en Pedidos. Envía los ítems con el precio mostrado y una referencia UUID. Pedidos verifica el precio vigente por REST y, en una transacción MySQL, guarda el pedido y descuenta las existencias. Devuelve la compra anterior si la confirmación se reintenta con la misma referencia. El pedido conserva el total y las líneas de la compra al momento de confirmarla.

Despacho consulta a Auth antes de asignar un repartidor. Envía los cambios de estado por una ruta interna de Pedidos. La entrega registra el pago en efectivo dentro del mismo pedido.

## Verificar

Ejecuta Java 17 y Maven Wrapper desde cada carpeta. En Windows:

```powershell
.\mvnw.cmd verify
```

[Prueba integrada](deploy/verificar-integracion.ps1) levanta los seis procesos y usa bases MySQL aisladas. Verifica roles, CRUD, precios, la última unidad, reintentos, caída de catálogo, despacho y contacto. El script usa el JAR compilado en `ms-auth/target/`. El parámetro opcional `-LegacyJar` prueba la migración desde el JAR de `ms-negocio`. Pasa `-Frontend`, `-Nginx` y `-Edge` para comprobar además el sitio compilado en el navegador. Mantén los archivos de entorno reales y los artefactos de compilación fuera de Git.
