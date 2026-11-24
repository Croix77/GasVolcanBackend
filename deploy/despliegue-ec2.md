# Ejecutar Gas El Volcán

Adaptación de la Guía 18. Nginx sirve React y envía las APIs a los puertos internos. MySQL o MariaDB guardan los datos. `systemd` mantiene los cinco JAR activos.

## 1. Configurar bases y variables

Crea los esquemas `gas_volcan_auth`, `gas_volcan_categorias`, `gas_volcan_productos` y `gas_volcan_pedidos` con caracteres `utf8mb4`. Crea una cuenta distinta para cada esquema y concede permisos solo sobre su esquema. Despacho consulta las APIs y utiliza los pedidos persistidos por `ms-pedidos`.

Cada proceso recibe la misma clave `JWT_SECRET` en Base64 de al menos 32 bytes y el emisor `gasvolcan-auth`. Usa una segunda clave, `CATALOGO_INTERNO_TOKEN`, compartida solo por Categorías y Productos para verificar asociaciones internas. Genera los valores para tu entorno y guárdalos en archivos de entorno con permisos `0600`.

| Servicio | Variables específicas |
| --- | --- |
| `ms-auth` | `AUTH_PORT=8081`, `AUTH_DB_URL`, `AUTH_DB_USER`, `AUTH_DB_PASSWORD` |
| `ms-categorias` | `PORT=8082`, `DB_URL`, `DB_USER`, `DB_PASSWORD`, `CATALOGO_INTERNO_TOKEN` |
| `ms-productos` | `PORT=8083`, `DB_URL`, `DB_USER`, `DB_PASSWORD`, `CATALOGO_INTERNO_TOKEN`, `CATEGORIAS_URL=http://127.0.0.1:8082` |
| `ms-pedidos` | `PORT=8084`, `DB_URL`, `DB_USER`, `DB_PASSWORD`, `PRODUCTOS_URL=http://127.0.0.1:8083` |
| `ms-despacho` | `PORT=8085`, `AUTH_URL=http://127.0.0.1:8081`, `PEDIDOS_URL=http://127.0.0.1:8084` |

En cada URL JDBC usa `jdbc:mysql://127.0.0.1:3306/` seguido del esquema del servicio. Para levantar una base nueva en local, configura `AUTH_DB_DDL_AUTO=update` o `DB_DDL_AUTO=update`. Para crear el primer administrador, sigue las instrucciones del [README de Auth](../ms-auth/README.md). Si estás migrando un sistema existente, prepara las tablas vacías y sigue la [migración](migracion.md).

Para una **demostración nueva** puedes cargar datos ficticios con `APP_SEED_CATALOGO=true` en Categorías y Productos y `APP_SEED_STOCK=true` en Pedidos. Inicia Categorías antes de Productos. En local arranca cada carpeta con `mvnw.cmd spring-boot:run`. Arranca React con `npm ci` y `npm run dev`. Su proxy Vite usa los cinco puertos de la tabla.

## 2. Compilar y publicar en EC2

1. Clona GasVolcanBackend y ejecuta `./mvnw verify` en cada una de sus cinco carpetas de servicio con Java 17. Copia los JAR compilados a `/opt/<nombre-del-servicio>/app.jar`.
2. Ejecuta `npm ci`, `npm test` y `npm run build` en React. Copia el contenido de `dist/` a `/var/www/gasvolcan`.
3. Crea el usuario de sistema `gasvolcan` con lectura de los JAR. Para cada servicio coloca su archivo `/etc/gasvolcan/<nombre-del-servicio>.env` con `SPRING_PROFILES_ACTIVE=prod` y las variables anteriores. Cambia la opción de DDL a `validate` después de preparar y comprobar las tablas. Desactiva las semillas después de iniciar.
4. Instala las cinco plantillas `.service` de esta carpeta en `/etc/systemd/system/`. Usa `sudo systemctl daemon-reload` y `sudo systemctl enable --now ms-auth ms-categorias ms-productos ms-pedidos ms-despacho`.
5. Instala [gasvolcan.conf](gasvolcan.conf), verifica con `sudo nginx -t` y recarga Nginx. El grupo de seguridad abre solo los puertos de ingreso necesarios para SSH y el sitio web. Los cinco servicios escuchan en loopback y las rutas `/api/v1/interno/` quedan fuera del sitio público.

Configura HTTPS si vas a usar el GPS del dispositivo. La entrada manual de coordenadas está disponible para la presentación. Al terminar, comprueba login, compra, asignación, entrega, historial y recargar una ruta de React. Revisa con `systemctl status` que los procesos vuelvan al reiniciar EC2.

Para Swagger usa túneles SSH a los puertos de los servicios y activa `SWAGGER_ENABLED=true` en los servicios documentados durante la revisión. Las rutas públicas permanecen en el dominio de Nginx.

## Docker del caso

La [plantilla Dockerfile](Dockerfile) empaqueta cada JAR por separado desde la carpeta del servicio. Cambia el primer argumento de archivo a la ruta que corresponde a este repositorio.

```sh
docker build -f /ruta/GasVolcanBackend/deploy/Dockerfile --build-arg JAR=target/ms-pedidos-0.0.1-SNAPSHOT.jar -t ms-pedidos .
```

En una instancia Linux con Nginx y MySQL en el host, la ejecución con `--network host` permite usar los puertos locales de la tabla. Aplicar los archivos de entorno y `SERVER_ADDRESS=127.0.0.1` antes de arrancar. Usa un método de arranque por servicio en cada puerto.
