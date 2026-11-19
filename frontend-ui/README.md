# Frontend — CinAsee UI

Interfaz web de CinAsee (React + Vite). Consume **siempre** el API Gateway del
Componente 5; nunca llama directamente a los microservicios.

- **Tecnología:** React 19 + Vite 8
- **Puerto de desarrollo:** `3000`
- **API base:** `http://localhost:8080` (Gateway)
- **Dependencias:** `axios`, `react-router-dom`

---

## 1. Comandos

```bash
cd frontend-ui

npm install       # instala dependencias
npm run dev       # servidor de desarrollo en http://localhost:3000
npm run build     # build de producción en dist/
npm run preview   # sirve el build
npm run lint      # ESLint
```

El puerto está fijado en `vite.config.js`:

```js
server: {
  port: 3000,
  host: true
}
```

### Variables de entorno

Copia `.env.example` a `.env` si necesitas apuntar a otro Gateway:

```bash
VITE_API_URL=http://localhost:8080
```

Y úsala en el código:

```js
const API = import.meta.env.VITE_API_URL ?? "http://localhost:8080";
```

---

## 2. Cómo se comunica con el resto

```
┌──────────────┐   HTTP + JWT    ┌──────────────┐
│  Frontend    │ ──────────────► │  C5 Gateway  │
│ :3000        │                 │ :8080        │
└──────────────┘                 └──────┬───────┘
                                        │
        ┌───────────────┬───────────────┼───────────────┐
        ▼               ▼               ▼               ▼
   C1 :8081        C2 :8082        C3 :8083        C4 :8084
   /api/v1/**      /v1/**          /v1/iam/**      /api/**
```

### Rutas que usa el frontend

| Necesidad | Llamada |
|---|---|
| Login | `POST /v1/iam/auth/login` |
| Registro | `POST /v1/iam/auth/register` |
| Token de invitado | `POST /v1/iam/auth/guest` |
| Mi perfil | `GET /v1/iam/users/{id}` |
| Cartelera | `GET /api/v1/movies?inShowcase=true` |
| Sesiones | `GET /api/v1/sessions?date=YYYY-MM-DD` |
| Pósteres | `GET /media/posters/<archivo>.jpg` |
| Butacas ocupadas | `GET /v1/reservations/shows/{showId}/seats` |
| Reservar | `POST /v1/reservations` |
| Pagar | `POST /v1/reservations/{id}/payment` |
| Mis entradas | `GET /v1/reservations` |
| Recomendaciones | `GET /api/recommendations/users/{id}` |
| Ocupación (staff) | `GET /api/metrics/occupancy` |
| Taquilla (staff) | `GET /api/reports/box-office` |

### Flujo típico

1. **Login** → guardar `token` (memoria o `sessionStorage`).
2. **Cartelera** → `GET /api/v1/movies?inShowcase=true` (público).
3. **Sesiones** → `GET /api/v1/sessions?movieId=...&date=...` (público).
4. **Mapa de butacas** → `GET /v1/reservations/shows/{showId}/seats` (público).
5. **Reserva** → `POST /v1/reservations` con el JWT.
6. **Pago** → `POST /v1/reservations/{id}/payment`.
7. **Entradas** → la respuesta incluye los códigos de los QR.

### Cliente HTTP sugerido

```js
// src/api.js
const API = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

export async function api(path, { token, ...options } = {}) {
  const response = await fetch(`${API}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  return response.status === 204 ? null : response.json();
}
```

```js
const { token } = await api("/v1/iam/auth/login", {
  method: "POST",
  body: JSON.stringify({ email, password }),
});

const movies = await api("/api/v1/movies?inShowcase=true");
```

---

## 3. Requisitos para que funcione

1. El stack levantado:

   ```bash
   docker compose up -d --build
   ```

2. El Gateway respondiendo en `http://localhost:8080`.
3. Cartelera con datos: ver
   [`../componente1-cartelera/docs/seed-cartelera.md`](../componente1-cartelera/docs/seed-cartelera.md).

Comprobación rápida:

```bash
curl http://localhost:8080/api/v1/movies
curl -X POST http://localhost:8080/v1/iam/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"usuario@cinasee.com","password":"Usuario123!"}'
```

---

## 4. Estado actual

`src/App.jsx` sigue siendo la **plantilla de Vite** (contador, enlaces a la
documentación de Vite). Todavía no hay páginas, rutas ni llamadas reales al API.

Lo que ya está listo:

- Proyecto Vite + React con el puerto 3000.
- Dependencias instaladas (`axios`, `react-router-dom`).
- CORS del Gateway apuntando a `http://localhost:3000`.
- Contrato de API documentado en el README raíz y en cada componente.

Lo que falta:

- Estructura de rutas (`react-router-dom`).
- Contexto de autenticación y almacenamiento del token.
- Pantallas: login, cartelera, sesiones, butacas, pago, entradas, panel de staff.
- Manejo de errores 401/403/429.

---

## 5. Credenciales de prueba

| Rol | Email | Contraseña |
|---|---|---|
| Admin | `admin@cinasee.com` | `Admin123!` |
| Staff | `staff@cinasee.com` | `Staff123!` |
| Cliente | `usuario@cinasee.com` | `Usuario123!` |

También se puede comprar como invitado con `POST /v1/iam/auth/guest` (token de
15 minutos).
