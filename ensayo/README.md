# Ensayo de Gas El Volcán por microservicio

Prueba local de la separación de los dominios. Se usa el JAR de `ms-auth` del repositorio de autenticación. El código de esta carpeta se hizo para ensayar los contratos HTTP y las reglas de inventario.

| Servicio | Responsabilidad en el ensayo |
| --- | --- |
| `ms-auth` | Login y JWT para cliente, administrador, operadora y repartidor |
| `ms-categorias` | Base propia y consulta de categorías |
| `ms-productos` | Base propia, fichas de dos cilindros y edición del precio |
| `ms-pedidos` | Pedido, historial, stock y cambios de estado en su base |
| `ms-despacho` | Valida al repartidor en Auth, recibe ubicación y coordina estados con Pedidos |

Pedidos solicita el precio a Productos antes de reservar stock. Si `precioEsperado` no coincide, responde `409`. El servidor guarda una copia del precio aceptado. Stock y pedido se guardan en una misma transacción. Una referencia de compra confirmada recupera el pedido incluso si Productos está fuera de servicio.

Despacho recibe las mutaciones en `/api/v1/despacho/`. Sus llamadas a Pedidos usan `/api/v1/interno/pedidos/`. Al desplegar, mantén los puertos Spring en la red privada y deja `/api/v1/interno/` fuera de las rutas del proxy público.

## Reproducir en Windows

Se requiere PowerShell 7, Java 17 y los binarios de MySQL. Compila los cuatro proyectos del ensayo y el proyecto de Auth desde su respectiva carpeta usando `mvnw.cmd verify`. Luego, desde la raíz de esta rama, ejecuta el ensayo indicando las rutas reales de los tres parámetros.

```powershell
.\ensayo\verificar.ps1 -AuthJar 'C:/ruta/ms-auth/target/ms-auth-0.0.1-SNAPSHOT.jar' -MysqlHome 'C:/ruta/mysql' -Java 'C:/ruta/jdk-17/bin/java.exe'
```

El script arranca los cinco procesos con puertos locales y una copia temporal de MySQL. Crea cuatro esquemas con cuentas ficticias, utiliza un JWT real de Auth y detiene los procesos que lanzó al terminar. La prueba cubre concurrencia por la última unidad, reintentos, reversión del stock, cambios de precio, caída de Productos, permisos y cierre de la entrega con pago en efectivo.

El ensayo usa dos productos de prueba. La integración completa necesita migrar categorías, productos, pedidos y stock existentes. También necesita conectar React a las nuevas rutas, exigir la confirmación del precio mostrado y probar esa versión en EC2.
