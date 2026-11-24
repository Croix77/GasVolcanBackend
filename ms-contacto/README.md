# ms-contacto

Recibe consultas, las guarda en MySQL y ofrece una bandeja de administración. Las consultas se gestionan dentro de la aplicación.

| Método | Ruta | Roles |
| --- | --- | --- |
| POST | `/api/v1/contacto/mensajes` | CLIENTE y ADMIN |
| GET | `/api/v1/contacto/mensajes?pagina=0` | ADMIN |

El POST recibe `nombre` (2–100 caracteres), `mensaje` (10–1000) y `referencia` (UUID). El servicio obtiene el correo de la sesión JWT. La confirmación devuelve el identificador y la fecha. La misma cuenta puede reenviar la referencia para recuperar la confirmación original. La bandeja lista 20 mensajes por página, primero los más recientes.

Configura el esquema `gas_volcan_contacto` y las variables `DB_URL`, `DB_USER`, `DB_PASSWORD` y `JWT_SECRET`. Usa el mismo secreto JWT que en `ms-auth`. El puerto local es `8086` y el emisor por defecto es `gasvolcan-auth`.

Desde esta carpeta, ejecuta `./mvnw verify` (`.\mvnw.cmd verify` en Windows). En entorno local, Swagger está disponible en `/swagger-ui/index.html`.
