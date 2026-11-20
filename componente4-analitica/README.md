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
| **C1 Cartelera** | Aforo de salas, títulos y géneros | Feign a `/rooms`, `/movies`, `/sessions` |
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
curl "http://localhost:8084/api/metrics/occupancy?cinemaId=550e8400-e29b-41d4-a716-446655440001&screenId=00000000-0000-4000-8000-000000000201&dayOfWeek=3&startTime=16:00&endTime=23:00" \
  -H "Authorization: Bearer $TOKEN"
```

```json
{
  "cinemaId": "550e8400-e29b-41d4-a716-446655440001",
  "screenId": "00000000-0000-4000-8000-000000000201",
  "dayOfWeek": 3,
  "timeRange": "16:00-23:00",
  "totalCapacity": 120,
  "totalTicketsSold": 42,
  "occupancyPercentage": 35.0
}
```

- `totalCapacity` → Componente 1. Con `screenId`, el aforo de esa sala
  (`GET /rooms/{id}`); sin `screenId`, la suma del aforo de las salas del cine
  (`GET /rooms?cinemaId=`) o de todas las salas (`GET /rooms`).
- `totalTicketsSold` → Componente 2 (`GET /reservations/stats`).
- `dayOfWeek` (1 = lunes … 7 = domingo) y la franja se filtran en C4 sobre
  `showStartTime`, usando `app.analytics.time-zone`.
- `cinemaId` **sí filtra**. Como C2 devuelve la venta por `roomId`, C4 primero
  pide a C1 las salas del cine (`GET /rooms?cinemaId=`) y después descarta las
  filas de C2 cuyo `roomId` no pertenece a ese conjunto. El aforo se calcula con
  esas mismas salas.
- Si `screenId` no está entre las salas del cine, C4 consulta `GET /rooms/{id}`:
  si la sala existe pero es de otro cine, devuelve aforo `0` y deja un
  `log.warn`.
- Si C1 no responde, C4 usa un aforo estimado (150 si se pide una sala, 500
  para el total) y **no** filtra la venta por cine, con `log.warn`. Si el cine
  no tiene salas, el aforo es `0` y la ocupación resultante también.

### Box Office

```bash
curl "http://localhost:8084/api/reports/box-office?startDate=2026-11-01&endDate=2026-11-30&movieId=1001" \
  -H "Authorization: Bearer $TOKEN"
```

```json
{
  "distributorId": null,
  "movieId": "550e8400-e29b-41d4-a716-446655440001",
  "movieTitle": "Dune: Parte Tres",
  "period": { "startDate": "2026-11-01", "endDate": "2026-11-30" },
  "totalTicketsSold": 120,
  "grossRevenue": 1020.00,
  "currency": "EUR"
}
```

- Tickets, reservas e ingresos → Componente 2.
- Título → Componente 1.
- **Limitación:** `distributorId` no se puede aplicar porque el catálogo ya no
  expone la distribuidora: el merge eliminó `Movie.distributor`. Se registra un
  aviso y se devuelve el informe sin ese filtro; el campo `distributor` llega
  `null`.

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

## 8. Tests

```bash
cd componente4-analitica
./mvnw test        # Windows: .\mvnw.cmd test
```

No necesitan Docker ni ningún otro servicio. El perfil `test`
(`src/test/resources/application-test.yml`) usa:

- **H2 en memoria** en modo PostgreSQL, en lugar de PostgreSQL.
- **Listener de RabbitMQ sin arrancar** (`auto-startup: false`), así que no
  intenta conectar al broker.
- **URLs ficticias** para C1, C2 y C3: los clientes Feign se sustituyen por mocks.

| Test | Tipo | Qué cubre |
|---|---|---|
| `AnalyticsServiceTest` | Unitaria (Mockito, `@Nested`) | Ocupación (filtros por cine, sala, día y franja; aforo estimado si C1 falla), Box Office, recomendaciones (puntuación y orden) y respaldo con `analytics_events` si C2 falla |
| `AnalyticsControllerTest` | Capa web (`@WebMvcTest`) | Endpoints de métricas, informes y recomendaciones; acceso por rol y propietario; validación de parámetros |
| `AnalyticsEventRepositoryTest` | Repositorio (`@DataJpaTest` + H2) | Agregaciones: entradas por cine, sala y día; media por cine; taquilla por película y periodo; géneros más vistos por usuario |
| `PaymentEventListenerTest` | Unitaria (Mockito) | Guardado del hecho de venta, enriquecimiento con título y género desde C1, y que un error no bloquee la cola |
| `PaymentCompletedContractTest` | Contrato | Deserialización del evento que publica C2 |
| `ForwardJwtFeignConfigTest` | Unitaria | Propagación del token hacia C1, C2 y C3 |
| `AnaliticaApplicationTests` | Arranque | El contexto arranca con el perfil `test` |

---

## 9. Decisiones y limitaciones conocidas

- **C2 como fuente principal, eventos como respaldo:** se evita duplicar la
  lógica de ventas y se mantiene el histórico para recomendaciones.
- **`cinemaId` filtrado en C4, no en C2:** C4 cruza las salas de C1 con la venta
  por `roomId` de C2, así que el filtro es correcto pero requiere una llamada
  extra al catálogo. C2 sigue sin recibir `cinemaId`; si en el futuro lo
  expone, C4 podrá pedirle la venta ya agregada por cine y ahorrarse el cruce.
- **Aforo estimado si C1 falla:** para no romper el informe, C4 devuelve una
  ocupación orientativa y avisa por log en lugar de propagar el error. Es una
  decisión consciente: un informe con datos parciales es preferible a un `500`.
- **`distributorId` sin efecto:** el catálogo ya no guarda la distribuidora
  (`Movie.distributor` se eliminó). Para que funcione habría que reintroducirla
  en C1, preferiblemente con id numérico.
- **Tests sobre H2, no sobre PostgreSQL:** las consultas de agregación se
  prueban con H2 en modo PostgreSQL. Es compatible para estas consultas, pero no
  idéntico a PostgreSQL.
- **Zona horaria explícita:** evita que un contenedor en UTC desplace las franjas
  horarias de las sesiones.
# Migración de IDs de Cartelera

Los IDs de cine, sala, sesión y película del Componente 1 se almacenan como
texto. Antes de desplegar esta versión sobre una base existente, detén el
servicio, haz un backup verificado y ejecuta
[`docs/migrations/001-catalog-ids-to-varchar.sql`](docs/migrations/001-catalog-ids-to-varchar.sql)
en PostgreSQL. Los valores históricos se conservan como cadenas decimales; la
migración no reasigna IDs.
