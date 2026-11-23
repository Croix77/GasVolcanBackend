# Gas El Volcán · Backend

El proyecto tiene dos microservicios Spring Boot. Cada uno tiene su propia carpeta Maven, puerto y base MySQL.

| Microservicio | Responsabilidad | Carpeta |
| --- | --- | --- |
| `ms-negocio` | Categorías, productos, stock, pedidos y despacho | [Abrir `ms-negocio/`](ms-negocio/) |
| `ms-auth` | Login, JWT, usuarios y roles | [Abrir `ms-auth/` en el repositorio de Auth](https://github.com/Croix77/GasVolcanAuth/tree/feat/ms-auth-parcial-3/ms-auth) |

Este repositorio contiene `ms-negocio/` y la configuración compartida de [despliegue](deploy/despliegue-ec2.md). El [frontend React](https://github.com/Croix77/GasvolcanReact/tree/feat/integracion-parcial-3) está en su repositorio.

## Abrir y verificar negocio

Abre `ms-negocio/` en el editor. La raíz Maven de este servicio es esa carpeta y contiene `pom.xml`, `src/` y `mvnw.cmd`.

```powershell
cd ms-negocio
.\mvnw.cmd verify
```

Para conectar MySQL y arrancar el servidor en local, sigue el [README de ms-negocio](ms-negocio/README.md).
