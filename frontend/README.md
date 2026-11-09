# Frontend

Aplicación web del marketplace en **React 19 + TypeScript + Vite**. El diseño sigue el mockup de Figma
"LR Alojamientos – Mockup interactivo" (enlazado en Confluence, página *Prototipo de la interfaz*).

## Requisitos

- Node.js 20 o superior (incluye npm)
- El backend corriendo en `http://localhost:8080` (ver `backend/README.md`)

## Cómo correrlo

```bash
cd frontend
npm install
npm run dev
```

Abre <http://localhost:5173>. Ese puerto es el que el backend acepta por defecto en CORS (`CORS_ORIGENES`).

Para usar otra URL del backend, copia `.env.example` como `.env.local` y cambia `VITE_API_URL`.

| Comando | Qué hace |
|---|---|
| `npm run dev` | Servidor de desarrollo con recarga automática |
| `npm run build` | Revisa los tipos y genera la versión de producción en `dist/` |
| `npm run lint` | Revisa el código con ESLint |
| `npm run preview` | Sirve localmente lo que generó `build` |

## Estructura

```
src/
├── api/            Cliente HTTP (fetch), errores Problem Details y llamadas por módulo
├── auth/           Sesión (token JWT), contexto y hook useAuth
├── components/     Componentes reutilizables (Logo, CampoTexto…)
├── pages/          Una carpeta por pantalla (login, provisionales…)
├── routes/         Rutas y guardias por rol (RF-03)
├── styles/         Tokens de diseño del mockup y estilos globales
└── types/          Tipos que reflejan los DTO del backend
```

## Autenticación

- `POST /api/auth/login` devuelve el token y el usuario. Se guarda en `localStorage` con su fecha de vencimiento.
- Cada petición autenticada envía `Authorization: Bearer <token>`.
- Si el backend responde 401 (token vencido o revocado, ADR-010), la sesión se cierra y se vuelve al login.
- Después del login cada rol va a su página: huésped `/huesped`, anfitrión `/anfitrion`, administrador `/admin`.
  Las guardias del frontend son solo de experiencia; el backend vuelve a validar el rol (ADR-009).

Para probar con el administrador, define `ADMIN_EMAIL` y `ADMIN_PASSWORD` en `backend/.env`.
