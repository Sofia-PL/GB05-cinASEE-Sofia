# Componente 5 — API Gateway (BFF)

Punto de entrada único para el frontend. Enruta las peticiones a los cuatro
microservicios, aplica CORS y rechaza peticiones sin un JWT válido en las rutas
protegidas.

- **Tecnología:** Java 21 + Spring Boot 4 (Spring Cloud Gateway, variante MVC)
- **Puerto:** `8080`
- **Sin base de datos**

---

## 1. Qué hace

| Función | Detalle |
|---|---|
| Enrutamiento | Prefijo de ruta → microservicio |
| CORS | Permite el origen del frontend (`http://localhost:3000`) |
| Validación temprana | Comprueba el JWT antes de reenviar (defensa en profundidad) |
| Punto único | El frontend solo conoce `http://localhost:8080` |

El Gateway **no** sustituye la seguridad de los microservicios: cada uno vuelve a
validar el token y aplica sus propios roles. Es una capa adicional, no la única.

---

## 2. Rutas

| Prefijo | Destino | Componente |
|---|---|---|
| `/api/v1/cinemas/**` | `http://componente1-cartelera:8081` | C1 Cines |
| `/api/v1/movies/**` | `http://componente1-cartelera:8081` | C1 Catálogo |
| `/api/v1/rooms/**` | `http://componente1-cartelera:8081` | C1 Catálogo |
| `/api/v1/sessions/**` | `http://componente1-cartelera:8081` | C1 Cartelera |
| `/v1/reservations/**` | `http://componente2-reservas:8082` | C2 Reservas |
| `/v1/iam/**` | `http://componente3-usuarios:8083` | C3 IAM |
| `/api/metrics/**` | `http://componente4-analitica:8084` | C4 Ocupación |
| `/api/reports/**` | `http://componente4-analitica:8084` | C4 Box Office |
| `/api/recommendations/**` | `http://componente4-analitica:8084` | C4 Recomendaciones |
| `/media/**` | `http://media-server:80` | Pósteres estáticos |

El prefijo se reenvía **tal cual**: el Gateway no reescribe rutas. Por eso los
`context-path` de cada servicio (`/api/v1`, `/v1`, `/v1/iam`, `/api`) ya están
incluidos en el destino.

---

## 3. Rutas públicas y protegidas

El filtro `JwtAuthenticationFilter` solo actúa sobre las rutas enrutadas. Deja
pasar sin token:

| Método | Ruta |
|---|---|
| `GET` | `/api/v1/cinemas/**` |
| `GET` | `/api/v1/movies/**` |
| `GET` | `/api/v1/sessions` |
| `GET` | `/v1/reservations/shows/*/seats` |
| `GET` | `/v1/iam/.well-known/**` |
| `GET` | `/v1/iam/actuator/health` |
| `POST` | `/v1/iam/auth/login`, `/register`, `/guest` |
| `OPTIONS` | Cualquiera (preflight CORS) |

El resto exige `Authorization: Bearer <token>`. Si falta o es inválido:

```json
{ "error": "unauthorized", "message": "A valid Bearer token is required" }
```

con estado **401** y cabecera `WWW-Authenticate: Bearer`.

La validación se hace en local con la clave pública del Componente 3
(`app.security.jwt.jwk-set-uri`) y comprobando el emisor. No se llama a C3 en
cada petición.

---

## 4. CORS

Configurado en `CorsConfig`:

| Propiedad | Valor |
|---|---|
| Origen permitido | `GATEWAY_CORS_ALLOWED_ORIGIN` (por defecto `http://localhost:3000`) |
| Métodos | `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS` |
| Cabeceras | `*` |
| Credenciales | `true` |

`PATCH` es necesario para cambiar el estado de una sesión de cartelera
(`PATCH /api/v1/sessions/{id}`).

---

## 5. Configuración

| Variable | Por defecto | Uso |
|---|---|---|
| `SERVER_PORT` | `8080` | Puerto |
| `COMPONENTE1_URI` | `http://localhost:8081` | C1 |
| `COMPONENTE2_URI` | `http://localhost:8082` | C2 |
| `COMPONENTE3_URI` | `http://localhost:8083` | C3 |
| `COMPONENTE4_URI` | `http://localhost:8084` | C4 |
| `GATEWAY_CORS_ALLOWED_ORIGIN` | `http://localhost:3000` | Origen del frontend |
| `JWT_JWK_SET_URI` | C3 | Clave pública |
| `JWT_ISSUER` | `http://localhost:8083/v1/iam` | Emisor esperado |
| `MEDIA_URI` | `http://media-server:80` | Servidor de pósteres |

En Docker las URLs apuntan a los nombres de servicio
(`http://componente1-cartelera:8081`, etc.). En local, a `localhost`.

---

## 6. Cómo lo usa el frontend

```js
const API = "http://localhost:8080";

const login = await fetch(`${API}/v1/iam/auth/login`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ email, password }),
}).then(r => r.json());

const movies = await fetch(`${API}/api/v1/movies?inShowcase=true`)
  .then(r => r.json());

const reservation = await fetch(`${API}/v1/reservations`, {
  method: "POST",
  headers: {
    "Content-Type": "application/json",
    Authorization: `Bearer ${login.token}`,
  },
  body: JSON.stringify({ showId: 3001, seats: [{ row: 4, column: 7 }] }),
}).then(r => r.json());
```

---

## 7. Decisiones y limitaciones

- **Gateway como BFF:** el frontend no necesita conocer los puertos internos.
- **Validación duplicada a propósito:** Gateway + microservicio. Si uno falla, el
  otro sigue protegiendo.
- **Sin rate limiting propio:** el único límite está en `/auth/guest` del C3.
- **Sin reescritura de rutas:** los `context-path` de los servicios forman parte
  del contrato público.
- **CORS de un solo origen:** suficiente para desarrollo; en producción habría
  que añadir el dominio real.
