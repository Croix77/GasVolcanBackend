# Despliegue en AWS EC2

Adaptación de la Guía 18 con Nginx, dos servicios Java y dos esquemas MySQL o MariaDB. Se ejecuta después de verificar las tres ramas en local.

## 1. Preparar los artefactos

- Ejecuta `./mvnw verify` con Java 17 desde `GasVolcanAuth/ms-auth` y `GasVolcanBackend/ms-negocio`.
- Ejecuta `npm ci`, `npm test` y `npm run build` en React. Elimina de ese entorno las variables `VITE_AUTH_URL` y `VITE_NEGOCIO_URL` para que el build use el mismo dominio que Nginx.
- En EC2, instala Java 17 o superior, MySQL o MariaDB y Nginx. Crea los esquemas `gas_volcan_auth` y `gas_volcan_negocio` con una cuenta distinta por esquema.
- Copia los JAR a `/opt/ms-auth/app.jar` y `/opt/ms-negocio/app.jar`. Copia el contenido de `dist/` a `/var/www/gasvolcan`.

## 2. Configurar secretos y servicios

Crea un usuario local de servicio `gasvolcan` con lectura de los JAR. Crea `/etc/gasvolcan/ms-auth.env` y `/etc/gasvolcan/ms-negocio.env` con permisos de lectura exclusivos para el administrador del servidor (`0600`). Ambos archivos deben tener `JWT_SECRET` con la misma clave Base64 y `SPRING_PROFILES_ACTIVE=prod`.

- Auth recibe `AUTH_DB_URL`, `AUTH_DB_USER`, `AUTH_DB_PASSWORD` y escucha por defecto en `8081`. Inicializa el esquema y el primer administrador con el procedimiento de su README. Para el primer arranque controlado usa `AUTH_DB_DDL_AUTO=update` y perfil `local`. Retira los parámetros de creación del administrador y vuelve a `prod` tras comprobar el acceso.
- Negocio recibe `DB_URL`, `DB_USER`, `DB_PASSWORD` y escucha por defecto en `8080`. Si su esquema está vacío, permite `DB_DDL_AUTO=update` durante el primer arranque. Para cargar el catálogo académico configura también `APP_SEED_CATALOGO=true`. Después usa `validate` y desactiva la carga inicial.
- Para Nginx con HTTPS, configura `APP_CORS_ORIGINS=https://tu-dominio` en ambos archivos si publicas ese dominio. El GPS del navegador requiere HTTPS o localhost. La interfaz permite marcar coordenadas manualmente.

Copia los dos archivos `.service` de esta carpeta a `/etc/systemd/system/`. Comprueba que el nombre de la cuenta del sistema coincida con `User=` antes de levantar ambos servicios.

```sh
sudo systemctl daemon-reload
sudo systemctl enable --now ms-auth ms-negocio
sudo systemctl status ms-auth ms-negocio --no-pager
```

## 3. Publicar con Nginx y probar

Instala el bloque de [gasvolcan.conf](gasvolcan.conf) en la configuración del sitio. Comprueba las reglas de AWS del puerto HTTP/HTTPS que vayas a utilizar. Los puertos de Java y MySQL se usan internamente. En una instancia con sitios existentes, asigna `server_name` al dominio de Gas El Volcán.

```sh
sudo nginx -t
sudo systemctl reload nginx
```

1. Entra como administrador y crea cuentas ficticias de cliente, vendedor, operadora y repartidor.
2. Con cada cuenta, comprueba su menú y prueba acceder a una ruta que le corresponda y a otra restringida.
3. Compra como cliente marcando el punto de entrega. Asigna desde la cuenta operadora, cambia el pedido a En camino, registra la ubicación con la cuenta asignada y termina con Entregado. Comprueba el pago y el mapa desde el historial.
4. Abre una ruta interna directamente desde el navegador y recárgala. Reinicia la instancia de prueba y comprueba que ambos servicios y Nginx vuelvan a iniciarse.

## Empaquetado Docker del caso

La plantilla [Dockerfile](Dockerfile) empaqueta cada JAR de forma independiente. Ejecuta los builds desde las carpetas de los respectivos servicios. El primer argumento de archivo debe apuntar al Dockerfile de esta carpeta.

```sh
# En GasVolcanAuth/ms-auth
docker build -f /ruta/GasVolcanBackend/deploy/Dockerfile --build-arg JAR=target/ms-auth-0.0.1-SNAPSHOT.jar -t ms-auth .
# En GasVolcanBackend/ms-negocio
docker build -f ../deploy/Dockerfile --build-arg JAR=target/ms-negocio-0.0.1-SNAPSHOT.jar -t ms-negocio .
```

Para mantener las bases y Nginx en el host Linux, puedes arrancar cada imagen con `--network host`, el archivo de entorno correspondiente y `-e SERVER_ADDRESS=127.0.0.1`. Usa un solo método de arranque por puerto en la misma instancia. La opción systemd reproduce el procedimiento de la Guía 18.
