# Despliegue con Docker Compose

Levanta el sistema completo en un equipo con Docker (MA-93):

| Pieza | Para qué | Dónde |
|---|---|---|
| Traefik | Balanceador: reparte `/api` entre las réplicas y sirve el frontend en `/` (ADR-008) | http://localhost |
| Backend × 2 | Réplicas idénticas y sin estado | Solo detrás de Traefik |
| Frontend | React servido por nginx | http://localhost |
| PostgreSQL 17 | Base de datos única | Solo dentro de Docker |
| Alloy + Loki | Recogen y guardan los logs JSON de todos los contenedores (ADR-012) | Solo dentro de Docker |
| Prometheus | Métricas de cada réplica y del balanceador | http://localhost:9090 |
| Grafana | Tablero de réplicas, tiempos de respuesta y logs | http://localhost:3000 |
| Panel de Traefik | Rutas y estado de cada réplica | http://localhost:8081 |

Grafana, Prometheus y el panel de Traefik solo se abren en este equipo (127.0.0.1).

## Requisitos

- Docker Desktop (Windows o macOS) o Docker Engine con Compose (Linux).
- El puerto 80 libre. Si está ocupado, cambia `PUERTO_APP` y `CORS_ORIGENES` en el `.env`.

## Levantarlo

Todo se ejecuta desde la carpeta `despliegue`.

1. Crea el archivo de variables: copia `.env.example` como `.env` y llena `DB_PASSWORD`, `JWT_SECRET`, `GRAFANA_ADMIN_PASSWORD`, `ADMIN_EMAIL` y `ADMIN_PASSWORD`. No se sube al repositorio.
2. Construye y arranca todo:
   ```
   docker compose up -d --build
   ```
   La primera vez tarda varios minutos: descarga las imágenes y compila el backend y el frontend.
3. Revisa que todo esté arriba:
   ```
   docker compose ps
   ```
   Deben aparecer `marketplace-backend-1` y `marketplace-backend-2`.
4. Abre http://localhost e inicia sesión con el administrador del `.env`.

Para apagarlo: `docker compose down`. Los datos quedan en volúmenes de Docker; `docker compose down -v` los borra.

Después de un cambio en el código: `docker compose up -d --build`.

## Ver el balanceo y la caída de una réplica (ASR-06)

1. Abre Grafana (http://localhost:3000, usuario `admin`) y el tablero **Marketplace - Réplicas, balanceador y logs**.
2. Usa la aplicación: en *Peticiones por segundo por réplica* se ve el tráfico repartido entre las dos.
3. Detén una réplica:
   ```
   docker stop marketplace-backend-1
   ```
   En máximo 5 s Traefik la retira (*Estado de cada réplica visto por Traefik* baja a 0) y la otra atiende todo. La aplicación sigue funcionando.
4. Vuelve a encenderla: `docker start marketplace-backend-1`. Traefik la incluye de nuevo cuando responde lista.

## Buscar una operación en los logs (ADR-012)

Cada respuesta de la API trae la cabecera `X-Correlation-Id`, y cada reserva guarda el suyo. En el tablero de Grafana escribe ese id en **Id de correlación**: el panel de logs muestra todos sus pasos, en cualquiera de las réplicas. También se puede consultar en *Explore* con Loki, por ejemplo:

```
{servicio="backend"} |= "el-id-de-correlación"
```

## Variables

Todas están en `.env.example`, con su explicación. Las obligatorias son `DB_PASSWORD`, `JWT_SECRET` y `GRAFANA_ADMIN_PASSWORD`: sin ellas Docker Compose no arranca y dice cuál falta.
