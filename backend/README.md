# Backend

API REST del marketplace. Java 21, Spring Boot 4.1 y PostgreSQL 17.

## Requisitos

- Java 21
- Docker Desktop, para PostgreSQL, el correo de prueba y las pruebas con Testcontainers

Maven no hace falta instalarlo: el proyecto trae el wrapper (`mvnw` / `mvnw.cmd`).

## Levantar el proyecto en local

Todo se ejecuta desde la carpeta `backend`.

1. Crea tu archivo de variables: copia `.env.example` como `.env` y cambia `DB_PASSWORD`.
2. Levanta PostgreSQL y Mailpit:
   ```
   docker compose up -d
   ```
3. Arranca la API:
   ```
   mvnw.cmd spring-boot:run      (Windows)
   ./mvnw spring-boot:run        (Linux / macOS / Git Bash)
   ```

Con la API arriba:

| Qué | Dónde |
|---|---|
| Health check (el que usa Traefik) | http://localhost:8080/actuator/health |
| Swagger | http://localhost:8080/swagger-ui.html |
| Correos de prueba (Mailpit) | http://localhost:8025 |

## Pruebas

```
mvnw.cmd verify
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

Se mantiene la organización por capas de Software 1 y se agregan los paquetes de las decisiones de arquitectura.

| Paquete | Capa | Qué va ahí |
|---|---|---|
| `controller` | Presentación | Controladores REST (`ControladorReservas`...) |
| `dto` | Presentación | Objetos de entrada y salida de la API |
| `service` | Aplicación | Casos de uso y transacciones (`GestionReservas`) |
| `model` | Dominio | Entidades JPA y `EstadoReserva` (State) |
| `repository` | Infraestructura | Repositorios Spring Data JPA |
| `pagos` | Infraestructura | Puerto `PasarelaPagos`, `StripeAdapter`, `SimuladorPagosAdapter` |
| `correo` | Infraestructura | `CorreoAdapter` (SMTP) |
| `outbox` | Infraestructura | `ProcesadorOutbox` |
| `scheduling` | Infraestructura | `TareasProgramadas` |
| `security` | Transversal | Spring Security, tokens, revocación de sesión |
| `filter` | Transversal | `CorrelacionFilter` (id de correlación) |
| `exception` | Transversal | Excepciones de negocio y `ManejadorGlobalExcepciones` |
| `configuration` | Transversal | OpenAPI, caché, ModelMapper |
| `util` | Transversal | Utilidades |

El esquema de la base de datos lo crean las migraciones de Flyway en `src/main/resources/db/migration`.
