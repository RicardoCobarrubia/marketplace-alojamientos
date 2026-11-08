# Marketplace de Alojamientos · Inversiones LR

MVP de un marketplace de alojamientos para Inversiones LR. Proyecto de Ingeniería de Software 2, Universidad El Bosque.

El MVP demuestra el flujo de punta a punta:

**publicación del alojamiento → búsqueda → consulta de disponibilidad → cotización → reserva inmediata**

## Estructura del repositorio

```
marketplace-alojamientos/
├── backend/     API REST · Java 21 · Spring Boot 4.1 (ver backend/README.md)
├── frontend/    Aplicación web · React
├── docs/        Documentación técnica que acompaña al código
└── .github/     Plantilla de Pull Request y flujos de GitHub Actions
```

La documentación de ingeniería (requerimientos, casos de uso, ASR, ADR, diagramas) vive en Confluence.

## Arquitectura

Monolito modular en capas (presentación, aplicación, dominio, infraestructura), con puertos y adaptadores solo en la integración de pagos (ADR-001). Se despliega en dos o más réplicas sin estado detrás de un balanceador, sobre una sola base de datos PostgreSQL (ADR-008).

| Pieza | Tecnología |
|---|---|
| Backend | Java 21, Spring Boot |
| Frontend | React |
| Base de datos | PostgreSQL |
| Balanceador | Traefik |
| Pagos | Stripe en modo pruebas, detrás del puerto `PasarelaPagos` |
| Logs | JSON con ID de correlación, Grafana Alloy, Loki y Grafana |

## Flujo de trabajo

Todo cambio que llegue a `main` recorre el flujo obligatorio del curso:

```
Historia Jira → branch → commits → Pull Request → GitHub Actions → pruebas → SonarCloud Quality Gate → revisión → merge
```

Las convenciones de ramas, commits y Pull Requests están en [CONTRIBUTING.md](CONTRIBUTING.md).

## Enlaces

- Jira: [proyecto MA](https://unbosque-proyectosoftware2.atlassian.net/jira/software/projects/MA/boards/1/backlog)
- Confluence: espacio del proyecto
