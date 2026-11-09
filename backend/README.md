# Backend

API REST del marketplace. Java 21, Spring Boot 4.1 y PostgreSQL 17.

## Requisitos

- Java 21
- Docker Desktop, para PostgreSQL, el correo de prueba y las pruebas con Testcontainers

Maven no hace falta instalarlo: el proyecto trae el wrapper (`mvnw` / `mvnw.cmd`).

## Levantar el proyecto en local

Todo se ejecuta desde la carpeta `backend`.

1. Crea tu archivo de variables: copia `.env.example` como `.env` y cambia `DB_PASSWORD`. Usa solo letras, números, `-` y `_`: Docker Compose interpreta el `$` y la base de datos y la API quedarían con claves distintas. Genera también tu propio `JWT_SECRET`; con el valor de ejemplo cualquiera podría firmar tokens.
2. Levanta PostgreSQL y Mailpit:
   ```
   docker compose up -d
   ```
3. Arranca la API. En Windows (cmd o PowerShell):
   ```
   mvnw.cmd spring-boot:run
   ```
   En Linux, macOS o Git Bash:
   ```
   ./mvnw spring-boot:run
   ```

Con la API arriba:

| Qué | Dónde |
|---|---|
| Health check (el que usa Traefik) | http://localhost:8080/actuator/health |
| Swagger | http://localhost:8080/swagger-ui.html |
| Correos de prueba (Mailpit) | http://localhost:8025 |

## Pruebas

En Windows (cmd o PowerShell):

```
mvnw.cmd verify
```

En Linux, macOS o Git Bash:

```
./mvnw verify
```

Corre las pruebas y deja la cobertura en `target/site/jacoco/`. Las pruebas de integración levantan un PostgreSQL con Testcontainers, así que Docker debe estar abierto.

## Perfiles

| Perfil | Cuándo | De dónde salen los valores |
|---|---|---|
| `dev` | Por defecto, en local | Archivo `.env` |
| `test` | Pruebas automáticas | Testcontainers |
| `prod` | Despliegue (`SPRING_PROFILES_ACTIVE=prod`) | Variables de entorno / GitHub Secrets |

Ningún archivo `.properties` guarda contraseñas ni llaves.

## Estructura de paquetes

Monolito modular en capas (ADR-001): un paquete por módulo de Confluence y, dentro de cada uno, las cuatro capas. Las dependencias van siempre hacia abajo y solo pagos y notificaciones usan puertos y adaptadores.

```
co.edu.unbosque.marketplace
├── comun             configuracion · excepcion · web (correlación y errores)
├── usuarios          registro, inicio de sesión y perfil
├── alojamientos      publicación, estados, servicios adicionales y catálogos
├── disponibilidad    periodos disponibles y consulta de disponibilidad
├── pagos             puerto PasarelaPagos y adaptadores (con la historia de reservas)
└── notificaciones    puerto de correo y procesador del outbox (con la historia de reservas)
```

| Capa | Qué va ahí | Puede usar |
|---|---|---|
| `presentacion` | Controladores REST | aplicación, dominio |
| `aplicacion` | Servicios (casos de uso, transacciones) y DTO | dominio, infraestructura |
| `dominio` | Entidades JPA, reglas del negocio y puertos | — |
| `infraestructura` | Repositorios Spring Data, seguridad y adaptadores | dominio |

Además, los módulos no pueden formar ciclos y `comun` no depende de ninguno. `ArquitecturaTest` (ArchUnit) comprueba estas reglas en cada pipeline, junto con la del ADR-006: el dominio y la aplicación no pueden importar el SDK de Stripe.

El esquema de la base de datos lo crean las migraciones de Flyway en `src/main/resources/db/migration`.
