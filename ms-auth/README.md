# Gas El Volcán · ms-auth

Microservicio Spring Boot que valida las credenciales, emite JWT firmados con HS256 y administra las cuentas. Usa una base MySQL propia. Los demás microservicios validan el JWT con el mismo emisor y la misma clave Base64.

## Preparación local

1. Usa Java 17 y crea la base `gas_volcan_auth` en MySQL.
2. Crea una cuenta de base de datos que tenga permisos solo sobre ese esquema. Usa una contraseña distinta de las contraseñas de la aplicación.
3. En una terminal abierta en esta carpeta, configura `JWT_SECRET` y `AUTH_DB_PASSWORD` antes de arrancar:

```powershell
$bytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
$claveDb = Read-Host "Contraseña MySQL de Auth" -AsSecureString
$env:AUTH_DB_PASSWORD = [System.Net.NetworkCredential]::new("", $claveDb).Password
.\mvnw.cmd spring-boot:run
```

Genera la clave JWT una vez por entorno y usa el mismo valor en todos los servicios. Auth escucha en `http://localhost:8081`; en local, Swagger abre en `http://localhost:8081/swagger-ui/index.html`.

## Primer usuario

Desde la carpeta del servicio, con una base vacía, habilita el perfil `local` e indica los datos del primer administrador antes de iniciar:

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
$env:AUTH_BOOTSTRAP_ADMIN = "true"
$env:AUTH_ADMIN_NAME = Read-Host "Nombre del administrador"
$env:AUTH_ADMIN_EMAIL = Read-Host "Correo del administrador"
$claveAdmin = Read-Host "Contraseña del administrador" -AsSecureString
$env:AUTH_ADMIN_PASSWORD = [System.Net.NetworkCredential]::new("", $claveAdmin).Password
.\mvnw.cmd spring-boot:run
```

La aplicación almacena la contraseña con BCrypt. Después del primer arranque, desactiva `AUTH_BOOTSTRAP_ADMIN` y elimina `AUTH_ADMIN_PASSWORD` del entorno de la terminal. Las cuentas de cliente, vendedor, operadora y repartidor se crean desde el panel de administración o por Swagger con el token de administrador.

Si usas una base de una versión anterior, el perfil local con `AUTH_DB_DDL_AUTO=update` agrega las columnas del nombre y de la versión de sesión. Cambia todas las contraseñas de las cuentas de ejemplo anteriores o prepara una base nueva. Para despliegue, prepara el esquema y el administrador inicial antes de arrancar con el perfil `prod` y su configuración de validación.

## Variables de entorno

| Variable | Valor predeterminado |
|---|---|
| `AUTH_PORT` | `8081` |
| `AUTH_DB_URL` | `jdbc:mysql://127.0.0.1:3306/gas_volcan_auth` |
| `AUTH_DB_USER` | `gas_volcan_auth_app` |
| `AUTH_DB_PASSWORD` | Requerida |
| `AUTH_DB_DDL_AUTO` | `update` local, `validate` en el perfil `prod` |
| `JWT_SECRET` | Requerida. Base64 de al menos 32 bytes |
| `JWT_ISSUER` | `gasvolcan-auth` |
| `AUTH_JWT_EXPIRATION_MS` | `3600000` (1 hora) |
| `AUTH_JWT_REFRESH_WINDOW_MS` | `300000` (5 minutos) |
| `APP_CORS_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` en local, vacío con `prod` |
| `SWAGGER_ENABLED` | `true` local, `false` con `prod` |

## API

| Método | Ruta | Permiso y datos |
|---|---|---|
| POST | `/api/auth/login` | Pública. Recibe `{correo,password}` |
| GET | `/api/auth/me` | Sesión válida. Devuelve el perfil actual |
| POST | `/api/auth/refresh` | Sesión válida. Usa el Bearer actual y emite un JWT nuevo durante la ventana de renovación |
| GET | `/api/auth/usuarios` | ADMIN. Lista de usuarios |
| POST | `/api/auth/usuarios` | ADMIN. Recibe `{nombre,correo,rol,password}` |
| GET | `/api/auth/usuarios/{id}` | ADMIN. Detalle |
| PUT | `/api/auth/usuarios/{id}` | ADMIN. Recibe `{nombre,rol,password?}` |
| PATCH | `/api/auth/usuarios/{id}/estado` | ADMIN. Recibe `{activo}` |
| GET | `/api/auth/repartidores` | ADMIN u OPERADORA. Lista de repartidores activos |

El login y la renovación devuelven `{token,correo,rol,nombre}`. El perfil y las operaciones de usuarios devuelven `{id,nombre,correo,rol,activo}`. El correo es la identidad que usa Negocio para asociar los pedidos y queda fijo al crear la cuenta. Los roles disponibles son `ADMIN`, `VENDEDOR`, `CLIENTE`, `OPERADORA` y `REPARTIDOR`.

La API devuelve `401` para login o sesión inválidos, `403` para permisos insuficientes, `400` para datos inválidos, `404` para un usuario inexistente y `409` para conflictos como un correo duplicado. Las respuestas de error contienen `mensaje` y los errores de validación de campos también pueden incluir `campos`.

Cambiar el rol o la contraseña, desactivar o reactivar una cuenta invalida los JWT anteriores en Auth. La renovación revisa el estado de la cuenta y requiere un JWT aún vigente. Fuera de los últimos cinco minutos devuelve el JWT actual.

## Pruebas

Ejecuta Maven desde la carpeta `ms-auth/`.

```powershell
.\mvnw.cmd -B -ntp verify
```

La suite incluye las pruebas unitarias de login y firma y pruebas HTTP con una base H2 de prueba. Se validan los permisos, cambios de estado, edición de usuarios, renovación, tokens vencidos y ruta OpenAPI. El JAR se genera en `ms-auth/target/ms-auth-0.0.1-SNAPSHOT.jar`.
