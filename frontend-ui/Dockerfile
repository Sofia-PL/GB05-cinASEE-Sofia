# =====================================================================
# Imagen del Frontend (React + Vite) - multi-stage build
#
#   Etapa 1 (build): instala dependencias y genera la web estática (dist/).
#   Etapa 2 (run):   nginx sirve esos ficheros en el puerto 80 (publicado en el 3000).
#
# La dirección del gateway se fija al compilar (Vite la incrusta en el JS):
#   docker build --build-arg VITE_API_URL=http://localhost:8080 -t cinasee/frontend .
# Es la dirección que usa el NAVEGADOR, por eso es localhost y no el nombre del contenedor.
# =====================================================================

FROM node:22-alpine AS build
WORKDIR /app

# 1) Dependencias primero: Docker las cachea mientras no cambie package-lock.json
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

# 2) Código y compilación
COPY . .
ARG VITE_API_URL=http://localhost:8080
ENV VITE_API_URL=${VITE_API_URL}
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
