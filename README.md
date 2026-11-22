# Gas El Volcán · Backend de negocio

Base del servicio de negocio para el catálogo y los pedidos de Gas El Volcán. El proyecto usa Java 17, Spring Boot 4.0.10, Maven y MySQL. El primer avance incluye el arranque del servicio y la conexión a la base de datos.

## Requisitos

- Java 17.
- MySQL activo en `127.0.0.1:3306`.
- Una base llamada `gas_volcan_negocio` y una cuenta `gas_volcan_app` con permisos sobre esa base. La contraseña se entrega por la variable de entorno `DB_PASSWORD`.

## Arrancar en Windows

Abre PowerShell en la carpeta que contiene `pom.xml`, con Java 17 disponible, y ejecuta:

```powershell
$clave = Read-Host "Contraseña de gas_volcan_app" -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new("", $clave).Password
.\mvnw.cmd spring-boot:run
```

El servicio inicia en el puerto `8080`. El mensaje `HikariPool-1 - Start completed` confirma la conexión a MySQL. Detén el servidor con `Ctrl+C`.

## Verificar

- `.\mvnw.cmd -DskipTests package` compila y genera el archivo JAR.
- `.\mvnw.cmd test` ejecuta la prueba de contexto con MySQL activo y `DB_PASSWORD` configurada.

El código Java está en `src/main/java`. La conexión a MySQL se configura en `src/main/resources/application.properties`. El siguiente bloque es el catálogo de categorías y productos.
