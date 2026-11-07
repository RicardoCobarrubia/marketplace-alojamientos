# Cómo trabajamos en este repositorio

## Ramas

Usamos un flujo simple: `main` es la única rama permanente y siempre debe estar estable. Todo trabajo se hace en una rama propia que sale de `main` y vuelve a `main` por Pull Request.

Cada rama lleva la clave de la historia de Jira, para que Jira la enlace sola:

| Tipo de cambio | Formato | Ejemplo |
|---|---|---|
| Funcionalidad | `feature/MA-<n>-descripcion-corta` | `feature/MA-25-reservar-alojamiento` |
| Corrección | `fix/MA-<n>-descripcion-corta` | `fix/MA-31-cotizacion-descuento` |
| Documentación | `docs/MA-<n>-descripcion-corta` | `docs/MA-14-readme-backend` |
| Configuración, CI | `chore/MA-<n>-descripcion-corta` | `chore/MA-8-pipeline-ci` |

Nunca se hace push directo a `main`.

## Commits

Cada commit es un cambio coherente y empieza con la clave de Jira:

```
MA-25: crear reserva en PENDIENTE_PAGO y retener fechas
MA-25: agregar pruebas de reservas simultáneas
```

Escribe el mensaje en presente y di qué cambia, no qué archivo tocaste.

## Pull Requests

1. Abre el PR hacia `main` y usa la plantilla. El título lleva la clave de Jira: `MA-25: Reservar alojamiento`.
2. GitHub Actions compila y corre las pruebas, y SonarCloud revisa el Quality Gate. Las dos verificaciones deben pasar.
3. Otro integrante del equipo revisa el PR. Las observaciones se corrigen o se responden con una justificación.
4. Solo se hace merge cuando todo lo anterior está aprobado.

## Antes de abrir el PR

- El código compila y las pruebas pasan en tu equipo.
- Las pruebas nuevas cubren los criterios de aceptación de la historia.
- No subiste credenciales, claves de Stripe ni archivos `.env`.
