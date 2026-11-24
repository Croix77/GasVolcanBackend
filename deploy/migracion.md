# Migrar desde ms-negocio

La migración copia el catálogo, las existencias y los pedidos verificables desde `gas_volcan_negocio` a tres esquemas vacíos. `ms-auth` continúa usando su base `gas_volcan_auth`.

1. Haz una copia de seguridad de `gas_volcan_negocio` y `gas_volcan_auth`. Detén la aplicación anterior durante la copia para que el inventario quede en un punto consistente.
2. Crea `gas_volcan_categorias`, `gas_volcan_productos` y `gas_volcan_pedidos` según [la guía de despliegue](despliegue-ec2.md). Genera las tablas arrancando los tres módulos con `DB_DDL_AUTO=update` y semillas desactivadas. Detén los tres antes de importar.
3. Con una cuenta de mantenimiento que pueda leer el origen y escribir el destino, ejecuta [migrar-negocio.sql](migrar-negocio.sql). El script conserva identificadores, códigos, precios, existencias, fechas e historial. Admite un esquema de origen con solo categorías. Los destinos con filas se rechazan para proteger datos existentes.
4. Compara los recuentos impresos con los de la copia anterior. Comprueba además el stock de un cilindro, los totales de un pedido y las cuentas de Auth. Arranca los cinco servicios y realiza la prueba de compra y despacho desde React. Conserva el respaldo hasta completar las comprobaciones.

En Windows, desde PowerShell puedes abrir la consola de MySQL y usar `source C:/ruta/GasVolcanBackend/deploy/migrar-negocio.sql`. Ejecuta el script antes de abrir la tienda del nuevo despliegue al público.
