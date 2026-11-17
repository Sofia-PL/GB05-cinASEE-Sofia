# Componente 4 — Analítica y Recomendaciones

Microservicio de **explotación de datos**: calcula ocupación, genera informes de
taquilla (*Box Office*) y recomienda películas a cada usuario.

- **Tecnología:** Java 21 + Spring Boot 4
- **Puerto:** `8084`
- **Ruta base:** `/api`
- **Base de datos:** PostgreSQL (`cine_analytics_dw`)
- **Mensajería:** RabbitMQ (consumidor de `PaymentCompleted`)

---

## 1. Qué hace

| Área | Responsabilidad |
|---|---|
| Ocupación | Entradas vendidas / aforo, por cine, sala, día y franja horaria |
| Box Office | Entradas e ingresos por película en un periodo |
| Recomendaciones | Ranking de películas según preferencias e historial |
| Histórico | Almacén de hechos de venta recibidos por RabbitMQ |

---

## 2. Fuentes de datos

Analítica **no lee bases de datos ajenas**. Todo se obtiene por HTTP o por
eventos:

| Fuente | Qué aporta | Cómo |
|---|---|---|
| **C2 Reservas** | Entradas vendidas, reservas e ingresos | `GET /reservations/stats`, `GET /reservations/box-office-data` |
| **C1 Cartelera** | Aforo de salas, títulos, géneros, distribuidora | Feign a `/rooms`, `/movies`, `/sessions` |
| **C3 Usuarios** | Perfil, puntos, historial de compras | Feign a `/users/{id}` |
| **RabbitMQ** | Hechos de venta (`analytics_events`) | Consumo de `payment.completed` |

```
                 ┌──────────────────────────────┐
   C1 Cartelera ─┤ aforo · títulos · géneros    │
                 │                              │
   C2 Reservas  ─┤ ventas · taquilla  (Feign)   ├──► C4 Analítica
                 │                              │
   C3 Usuarios  ─┤ perfil · historial           │
                 └──────────────────────────────┘
                              ▲
   C2 ── payment.completed ───┘  (RabbitMQ → analytics_events)
```

### Por qué C2 es la fuente principal de ventas

El Componente 2 es el dueño transaccional de las reservas: sabe exactamente qué
butacas se han pagado. Sus endpoints agregados (`/reservations/stats` y
`/reservations/box-office-data`) solo cuentan reservas `CONFIRMED`, así que son
la fuente de verdad.

Los eventos de RabbitMQ siguen guardándose en `analytics_events` porque
alimentan las recomendaciones (géneros vistos) y sirven de **respaldo** si C2 no
responde. En ese caso se registra un `WARN` y se calcula con los eventos
locales.

---

## 3. Arquitectura interna

```
es.cineasee.analitica
├── clients/       CarteleraClient, UsuariosClient, ReservasClient,
│                  ForwardJwtFeignConfig (token relay)
│   └── dto/       MovieDTO, RoomDTO, SessionDTO, UserProfileDTO,
│                  PurchaseHistoryItemDTO, ShowStatsDTO, BoxOfficeDataDTO
├── config/        SecurityConfig, RabbitMQConfig
├── controllers/   AnalyticsController
├── dto/           OccupancyMetricDTO, BoxOfficeReportDTO,
│                  RecommendationResponseDTO
├── events/        PaymentCompletedEvent + PaymentEventListener
├── models/        AnalyticsEvent (tabla de hechos)
├── repositories/  AnalyticsEventRepository
└── services/      AnalyticsService
```

---

## 4. Endpoints

Ruta base: `http://localhost:8084/api`.

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| `GET` | `/metrics/occupancy` | `ADMIN`, `STAFF_TAQUILLA` | Tasa de ocupación |
| `GET` | `/reports/box-office` | `ADMIN`, `STAFF_TAQUILLA` | Informe de taquilla |
| `GET` | `/recommendations/users/{userId}` | El propio usuario, `ADMIN` o `STAFF_TAQUILLA` | Recomendaciones |

### Ocupación

```bash
curl "http://localhost:8084/api/metrics/occupancy?cinemaId=1&screenId=2001&dayOfWeek=3&startTime=16:00&endTime=23:00" \
  -H "Authorization: Bearer $TOKEN"
```

```json
{
  "cinemaId": 1,
  "screenId": 2001,
  "dayOfWeek": 3,
  "timeRange": "16:00-23:00",
  "totalCapacity": 120,
  "totalTicketsSold": 42,
  "occupancyPercentage": 35.0
}
```

- `totalCapacity` → Componente 1 (`GET /rooms/{id}` o suma de `/rooms`).
- `totalTicketsSold` → Componente 2 (`GET /reservations/stats`).
- `dayOfWeek` (1 = lunes … 7 = domingo) y la franja se filtran en C4 sobre
  `showStartTime`, usando `app.analytics.time-zone`.
- `cinemaId` se acepta por compatibilidad con el OpenAPI, pero **no filtra**: el
  catálogo del Componente 1 no modela cines.

### Box Office

```bash
curl "http://localhost:8084/api/reports/box-office?startDate=2026-11-01&endDate=2026-11-30&movieId=1001" \
  -H "Authorization: Bearer $TOKEN"
```

```json
{
  "distributorId": null,
  "movieId": 1001,
  "movieTitle": "Dune: Parte Tres",
  "period": { "startDate": "2026-11-01", "endDate": "2026-11-30" },
  "totalTicketsSold": 120,
  "grossRevenue": 1020.00,
  "currency": "EUR"
}
```

- Tickets, reservas e ingresos → Componente 2.
- Título → Componente 1.
- **Limitación:** `distributorId` no se puede aplicar porque el catálogo guarda
  la distribuidora como texto (`"Warner Bros."`), no como id numérico. Se
  registra un aviso y se devuelve el informe sin ese filtro.

### Recomendaciones

```bash
curl "http://localhost:8084/api/recommendations/users/3?limit=5" \
  -H "Authorization: Bearer $TOKEN"
```

Puntuación:

| Situación | Ajuste |
|---|---|
| Puntuación base | `0.50` |
| Género preferido del perfil | `+0.45` |
| Género visto en el historial | `+0.35` |
| Película ya vista | `-0.60` |
| Rango final | `0.10` – `0.99` |

Se ordenan por afinidad descendente y se recortan a `limit` (por defecto 5).

---

## 5. Integración con Reservas (C2)

`ReservasClient` es un cliente OpenFeign:

```java
@FeignClient(
    name = "reservas-service",
    url = "${services.reservas.url:http://localhost:8082/v1}",
    configuration = ForwardJwtFeignConfig.class
)
```

- **URL configurable:** `RESERVAS_SERVICE_URL`.
- **Token relay:** `ForwardJwtFeignConfig` copia el `Authorization: Bearer` del
  usuario que llamó a Analítica. Reservas exige `ADMIN`/`STAFF_TAQUILLA` en esos
  endpoints, así que C4 debe invocarse con uno de esos roles.
- **Timeouts:** 2 s de conexión y 5 s de lectura (`FEIGN_CONNECT_TIMEOUT_MS`,
  `FEIGN_READ_TIMEOUT_MS`), para que el respaldo salte rápido si C2 no responde.
- **Fallback:** si la llamada falla, se usa `analytics_events`.

```
Frontend/Postman ──JWT──► C4 ──mismo JWT──► C2 /reservations/stats
```

---

## 6. Eventos

`PaymentEventListener` consume `payment.completed.queue` y guarda un
`AnalyticsEvent` con:

`reservationId`, `userId`, `cinemaId`, `roomId`, `sessionId`, `movieId`,
`movieTitle`, `genre`, `distributor`, `ticketsSold`, `totalAmount`, `dayOfWeek`,
`sessionTime`, `eventTimestamp`.

Si el payload no trae título/género, se enriquecen consultando Cartelera. Los
errores se registran y no se relanzan para no bloquear la cola.

---

## 7. Configuración

| Variable | Por defecto | Uso |
|---|---|---|
| `SERVER_PORT` | `8084` | Puerto |
| `DB_HOST` / `DB_PORT` | `localhost` / `5432` | PostgreSQL |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `postgres` | Credenciales |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | Broker |
| `CARTELERA_SERVICE_URL` | `http://localhost:8081/api/v1` | Comp. 1 |
| `RESERVAS_SERVICE_URL` | `http://localhost:8082/v1` | Comp. 2 |
| `USUARIOS_SERVICE_URL` | `http://localhost:8083/v1/iam` | Comp. 3 |
| `ANALYTICS_TIME_ZONE` | `Europe/Madrid` | Zona para día/franja |
| `JWT_JWK_SET_URI` / `JWT_ISSUER` | C3 | Validación de tokens |

> `ANALYTICS_TIME_ZONE` debe coincidir con `CATALOG_TIME_ZONE` del Componente 2.

---

## 8. Decisiones y limitaciones conocidas

- **C2 como fuente principal, eventos como respaldo:** se evita duplicar la
  lógica de ventas y se mantiene el histórico para recomendaciones.
- **`cinemaId` sin efecto (C1 no modela cines):** el catálogo solo tiene
  películas, salas y sesiones, así que el parámetro se mantiene por
  compatibilidad con el OpenAPI pero no filtra. Hoy se usa `cinemaId = 1`.
- **`distributorId` sin efecto:** el catálogo guarda la distribuidora como
  texto. Para que funcione habría que añadir `distributorId` a `Movie` en C1.
- **Sin perfil de test aislado:** el único test es
  `AnaliticaApplicationTests.contextLoads`, un `@SpringBootTest` que necesita
  PostgreSQL. Sin la base de datos levantada, `mvn test` falla al crear el
  `EntityManagerFactory`. Para ejecutarlo hay que arrancar `postgres-analitica`
  o añadir un perfil de test con H2.
- **Zona horaria explícita:** evita que un contenedor en UTC desplace las franjas
  horarias de las sesiones.
