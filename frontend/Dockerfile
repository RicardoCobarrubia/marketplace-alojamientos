# Imagen del frontend para el despliegue con Docker Compose (MA-93).
# Se construye desde la carpeta frontend:  docker build -t marketplace-frontend .

# ─── 1. Compilar ─────────────────────────────────────────
FROM node:22-alpine AS construccion
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Sin URL: la API se llama en la misma dirección (/api) y Traefik la envía al backend
ENV VITE_API_URL=""
RUN npm run build

# ─── 2. Servir ───────────────────────────────────────────
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=construccion /app/dist /usr/share/nginx/html
EXPOSE 80
