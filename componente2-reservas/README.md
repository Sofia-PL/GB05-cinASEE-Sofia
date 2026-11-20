# Componente 2 — Motor de Reservas y Pagos

Microservicio transaccional de CinAsee. Gestiona el bloqueo de butacas, el
cálculo de precios, el pago, la emisión de entradas y los informes de ventas.

- **Tecnología:** Java 21 + Spring Boot 4
- **Puerto:** `8082`
- **Ruta base:** `/v1`
- **Base de datos:** PostgreSQL (`cine_reservas_db`)
- **Mensajería:** RabbitMQ (publica `PaymentCompleted` y `PaymentRefunded`)
- **Pasarela:** Redsys (modo pruebas) o simulada

---

## 1. Qué hace

| Área | Responsabilidad |
|---|---|
| Reservas | Bloqueo temporal de butacas con TTL y control de concurrencia |
| Precios | Precio base + descuentos (día del espectador, fidelización) |
| Pagos | Pago simulado o Redsys, confirmación y reembolso |
| Entradas | Emisión de un código único por butaca y check-in en puerta |
| Informes | Agregados de ventas que consume el Componente 4 |
| Eventos | Publicación fiable de `PaymentCompleted` mediante patrón *outbox* |

---

## 2. Arquitectura interna

```
com.cinasee.componente2_reservas
├── clients/      Feign hacia Cartelera y Usuarios (+ ForwardJwtFeignConfig)
├── config/       Seguridad, propiedades, RabbitMQ, reloj, CORS
├── controllers/  ReservationController, TicketController, ReportController,
│                 RedsysReturnController
├── dto/          Requests y respuestas del OpenAPI
├── events/       Contratos y publicación (outbox relay)
├── exceptions/   Errores de dominio traducidos a códigos HTTP
├── models/       Reservation, ReservedSeat, OutboxEvent
├── repositories/ Spring Data JPA + consultas de agregación
├── security/     CallerIdentity (identidad extraída del JWT)
└── services/     ReservationService, SeatLockService, PricingService,
                  PaymentService, RedsysPaymentService, CancellationService,
                  TicketService, ReportService, OutboxService
```

---

## 3. Flujo de compra

```
Cliente                    C2 Reservas                 C1 Cartelera      C3 Usuarios
   │                           │                           │                 │
   │ POST /reservations        │                           │                 │
   ├──────────────────────────►│                           │                 │
   │                           ├── GET /sessions/{id} ────►│                 │
   │                           ├── GET /rooms/{id} ───────►│                 │
   │                           ├── GET /users/{id} ────────────────────────►│
   │                           │                           │                 │
   │                           │  bloquea butacas (10 min) │                 │
   │◄──── 201 + TTL ───────────┤                           │                 │
   │                           │                           │                 │
   │ POST /{id}/payment        │                           │                 │
   ├──────────────────────────►│  cobra + confirma         │                 │
   │                           │  emite tickets            │                 │
   │                           │  guarda evento en outbox  │                 │
   │◄──── 200 + tickets ───────┤                           │                 │
   │                           │                           │                 │
   │                           │  outbox relay ──► RabbitMQ ──► C3 (puntos)
   │                           │                              └► C4 (analítica)
```

### Estados de una reserva

```
                 pago OK
PENDING_PAYMENT ─────────► CONFIRMED ── devolución ──► REFUNDED  (libera butacas)
     │  │
     │  └── TTL agotado ──► EXPIRED                               (libera butacas)
     └───── cancelación ──► CANCELLED                            (libera butacas)
```

Solo `PENDING_PAYMENT` (dentro del TTL) y `CONFIRMED` ocupan butacas.

---

## 4. Endpoints

Ruta base: `http://localhost:8082/v1`.

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| `POST` | `/reservations` | JWT (incluido invitado) | Crea reserva y bloquea butacas |
| `GET` | `/reservations` | JWT | Mis reservas (`userId` solo para staff) |
| `GET` | `/reservations/{id}` | JWT / propietario | Detalle y TTL restante |
| `POST` | `/reservations/{id}/payment` | JWT / propietario | Pago (simulado o gateway) |
| `POST` | `/reservations/{id}/payment/redsys` | JWT / propietario | Formulario firmado de Redsys |
| `POST` | `/reservations/{id}/payment/redsys/confirm` | JWT / propietario | Confirma la vuelta de Redsys |
| `DELETE` | `/reservations/{id}` | JWT / propietario | Cancela o devuelve |
| `GET` | `/reservations/shows/{showId}/seats` | Público | Mapa de butacas ocupadas |
| `POST` | `/reservations/tickets/{ticketCode}/check-in` | `ADMIN`, `STAFF_TAQUILLA` | Validación en puerta |
| `GET` | `/reservations/stats` | `ADMIN`, `STAFF_TAQUILLA` | Entradas vendidas por sesión |
| `GET` | `/reservations/box-office-data` | `ADMIN`, `STAFF_TAQUILLA` | Taquilla por película |
| `GET/POST` | `/reservations/payments/redsys/return` | Público | Página de vuelta de Redsys |

Ejemplo de reserva:

```bash
curl -X POST http://localhost:8082/v1/reservations \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "showId": "01ARZ3NDEKTSV4RRFFQ69G5FAV",
    "seats": [{"row": 4, "column": 7}, {"row": 4, "column": 8}],
    "guestEmail": "invitado@example.com"
  }'
```

---

## 5. Control de concurrencia

La tabla `reserved_seats` tiene una restricción **UNIQUE
`(show_id, seat_row, seat_column)`**. Si dos usuarios intentan la misma butaca a
la vez, PostgreSQL acepta una fila y rechaza la otra, aunque ambas hayan pasado
las comprobaciones en memoria. Es la garantía final contra la doble venta.

- El bloqueo dura **10 minutos** (`RESERVATION_HOLD_TTL`).
- Un barrido periódico (`RESERVATION_SWEEP_INTERVAL`, 30 s) expira reservas y
  libera butacas.
- Durante el pago en Redsys hay un margen extra (`RESERVATION_PAYMENT_GRACE`,
  15 min) para que no caduque mientras el cliente está en la pasarela.
- Las llamadas Feign se hacen **fuera** de la transacción de base de datos, para
  no mantener bloqueos mientras se espera a otro microservicio.

---

## 6. Precios

`PricingService` trabaja con `BigDecimal` (nunca `double`).

| Concepto | Regla |
|---|---|
| Precio base | `ticketPrice` de la sesión; si falta, `basePrice` de la sala |
| Día del espectador | 20 % los miércoles (zona `Europe/Madrid`) |
| Fidelización | 10 % si el usuario tiene ≥ 500 puntos (consultado a C3) |

Los descuentos **no se acumulan**: se aplica el mayor.

---

## 7. Integración síncrona (OpenFeign)

| Destino | Endpoint usado | Para qué |
|---|---|---|
| C1 Cartelera | `GET /sessions/{id}` | Validar sesión, estado y hora |
| C1 Cartelera | `GET /rooms/{id}` | Validar butacas y precio de respaldo |
| C3 Usuarios | `GET /users/{id}` | Perfil y puntos de fidelización |

El token del usuario se propaga con `ForwardJwtFeignConfig` (*token relay*).
Timeouts: conexión 2 s, lectura 5 s (`FEIGN_CONNECT_TIMEOUT_MS`,
`FEIGN_READ_TIMEOUT_MS`).

URLs configurables:

```yaml
app:
  clients:
    cartelera:
      url: ${CARTELERA_URL:http://localhost:8081/api/v1}
    usuarios:
      url: ${USUARIOS_URL:http://localhost:8083/v1/iam}
```

---

## 8. Eventos (outbox + RabbitMQ)

Para no perder eventos si RabbitMQ falla, el pago guarda primero el evento en la
tabla `outbox_events` dentro de la misma transacción. Un relay lo publica cada
2 segundos con *publisher confirms* y lo marca como enviado.

| Elemento | Valor |
|---|---|
| Exchange | `payment.exchange` (topic) |
| Routing key | `payment.completed` / `payment.refunded` |
| Cola C3 | `user.payment.completed` |
| Cola C4 | `payment.completed.queue` |

### Contrato de `PaymentCompleted`

El mismo mensaje lo consumen C3 y C4. Cada uno deserializa los campos que
conoce e ignora el resto (`@JsonIgnoreProperties(ignoreUnknown = true)`).

```json
{
  "eventId": "3f2c7a1e-9b1d-4f2a-8c3e-1a2b3c4d5e6f",
  "reservationId": 8901,
  "userId": 42,
  "movieId": "550e8400-e29b-41d4-a716-446655440001",
  "amount": 18.50,
  "currency": "EUR",
  "purchaseDate": "2026-09-20T18:30:00Z",
  "occurredAt": "2026-09-20T18:30:05Z",
  "showId": "01ARZ3NDEKTSV4RRFFQ69G5FAV",
  "sessionId": "01ARZ3NDEKTSV4RRFFQ69G5FAV",
  "roomId": "550e8400-e29b-41d4-a716-446655440002",
  "ticketsCount": 2,
  "totalAmount": 18.50,
  "showStartTime": "2026-09-21T18:00:00Z",
  "timestamp": "2026-09-21T20:00:00"
}
```

- `userId` es `null` en compras de invitado (C3 no asigna puntos).
- `amount` y `totalAmount` son el mismo importe con distinto nombre: C3 usa el
  primero y C4 el segundo.
- `showId` y `sessionId` son el mismo identificador.
- `timestamp` es la hora local de inicio de la sesión, sin zona, porque C4 la
  usa para día de la semana y franja horaria.

---

## 9. Informes para el Componente 4

### `GET /reservations/stats`

```json
[
  {
    "showId": "01ARZ3NDEKTSV4RRFFQ69G5FAV",
    "movieId": "550e8400-e29b-41d4-a716-446655440001",
    "roomId": "550e8400-e29b-41d4-a716-446655440002",
    "showStartTime": "2026-11-18T15:00:00Z",
    "ticketsSold": 42,
    "reservations": 21,
    "revenue": 357.00,
    "currency": "EUR"
  }
]
```

Filtros opcionales: `showId`, `roomId`, `from`, `to` (fechas ISO, inclusivas).

### `GET /reservations/box-office-data`

```json
{
  "startDate": "2026-11-01",
  "endDate": "2026-11-30",
  "currency": "EUR",
  "movies": [
    { "movieId": "550e8400-e29b-41d4-a716-446655440001", "totalTicketsSold": 120, "reservations": 60, "grossRevenue": 1020.00 }
  ]
}
```

`startDate` y `endDate` son obligatorios; el rango máximo es de **366 días**.

> C2 solo expone **sus** datos (butacas e importes). El cruce con aforos y
> títulos lo hace el Componente 4 consultando a C1. Ningún servicio lee la base
> de datos de otro.

---

## 10. Redsys

`PAYMENT_MODE` selecciona la pasarela:

- `simulated`: pago inmediato, útil para tests y ejecución aislada.
- `redsys`: usa la **web de pruebas real** de Redsys con los datos genéricos que
  Redsys publica para todos los desarrolladores.

Los datos de prueba (`merchant-code`, `terminal`, `secret-key`) están en
`application.yaml` y **no son credenciales reales**. Para un comercio real hay
que pasarlos por variables de entorno (`REDSYS_SECRET_KEY`,
`REDSYS_MERCHANT_CODE`, `REDSYS_TERMINAL`, `REDSYS_PAYMENT_URL`) o un gestor de
secretos.

Flujo:

1. `POST /{id}/payment/redsys` devuelve un formulario firmado.
2. El navegador se envía a Redsys.
3. Redsys devuelve al cliente a `url-ok`/`url-ko`.
4. `RedsysReturnController` (o el frontend) reenvía los parámetros a
   `/{id}/payment/redsys/confirm`, que valida la firma y emite las entradas.

La integración de Redsys la mantiene otro compañero; este documento solo
describe lo que ya existe.

---

## 11. Configuración

| Variable | Por defecto | Uso |
|---|---|---|
| `SERVER_PORT` | `8082` | Puerto |
| `POSTGRES_HOST` / `POSTGRES_PORT` | `localhost` / `5432` | PostgreSQL |
| `POSTGRES_DB` | `cine_reservas_db` | Base de datos |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | `postgres` / `postgres` | Credenciales |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | Broker |
| `CARTELERA_URL` | `http://localhost:8081/api/v1` | Comp. 1 |
| `USUARIOS_URL` | `http://localhost:8083/v1/iam` | Comp. 3 |
| `JWT_JWK_SET_URI` / `JWT_ISSUER` | C3 | Validación de tokens |
| `RESERVATION_HOLD_TTL` | `10m` | Bloqueo de butacas |
| `RESERVATION_SWEEP_INTERVAL` | `30s` | Barrido de expiradas |
| `RESERVATION_PAYMENT_GRACE` | `15m` | Margen durante el pago |
| `CATALOG_TIME_ZONE` | `UTC` (compose: `Europe/Madrid`) | Zona de las sesiones de C1 |
| `PAYMENT_MODE` | `simulated` | `simulated` o `redsys` |
| `SPECTATOR_DAY` | `WEDNESDAY` | Día del espectador |
| `LOYALTY_POINTS_THRESHOLD` | `500` | Umbral de descuento |

---

## 12. Tests

```bash
cd componente2-reservas
./mvnw test        # Windows: .\mvnw.cmd test
```

No necesitan Docker ni ningún otro servicio. El perfil `test`
(`src/test/resources/application-test.yaml`) usa:

- **H2 en memoria** en modo PostgreSQL, en lugar de PostgreSQL.
- **Mocks** (`@MockitoBean`) para los clientes Feign de C1 y C3 y para el
  publicador de RabbitMQ.
- **Pasarela simulada** y una clave de Redsys inventada solo para tests. Nunca se
  conecta a Redsys.
- **Tareas programadas desactivadas:** el barrido de expiradas y el relay del
  outbox se invocan a mano para que los tests sean deterministas.
- **Reloj controlado** (`MutableClock`) para avanzar el tiempo y probar los TTL
  sin esperar.

| Test | Tipo | Qué cubre |
|---|---|---|
| `ReservationFlowIntegrationTest` | Integración (`@SpringBootTest` + H2) | Doble venta imposible con concurrencia real (varios hilos a la vez), bloqueo hasta que caduca el TTL, barrido periódico, flujo reservar → pagar → `PaymentCompleted`, cancelación y devolución (`PaymentRefunded`), check-in una sola vez, informes que solo cuentan `CONFIRMED` |
| `RedsysFlowIntegrationTest` | Integración (`@SpringBootTest` + H2) | Flujo completo con Redsys, butacas protegidas durante el pago, pago abandonado que caduca al acabar el margen, pago denegado y reintento con otro pedido |
| `ReservationControllerTest` | Capa web (`@WebMvcTest`) | Contrato HTTP, validación, códigos de estado y permisos por rol y propietario |
| `ReservationServiceTest` | Unitaria (Mockito) | Creación de reservas, validación contra el catálogo y transiciones de estado |
| `PricingServiceTest` | Unitaria | Precio base, día del espectador, descuento por puntos y que los descuentos no se acumulen |
| `PaymentServiceTest`, `RedsysPaymentServiceTest` | Unitaria (Mockito) | Pago simulado y Redsys: confirmación, rechazo y respuestas inválidas |
| `RedsysSignatureTest` | Unitaria | Firma y verificación HMAC-SHA256 + 3DES; clave de longitud incorrecta |
| `TicketServiceTest`, `TicketCodeGeneratorTest` | Unitaria | Emisión y validación de entradas; códigos únicos |
| `ReservationAccessPolicyTest`, `CallerIdentityTest` | Unitaria | Quién puede ver o modificar cada reserva; identidad extraída del JWT |
| `ReservationExpirationSchedulerTest` | Unitaria (Mockito) | Barrido de reservas caducadas |
| `OutboxRelayTest` | Unitaria (Mockito) | Publica los pendientes y los marca; con el broker caído anota el error y detiene el lote |
| `PaymentCompletedContractTest` | Contrato | El JSON publicado lo pueden leer C3 y C4, incluida la compra de invitado (`userId` nulo) |
| `RemoteDataServiceTest`, `ForwardJwtFeignConfigTest` | Unitaria | Llamadas a C1/C3 y sus errores; propagación del token (*token relay*) |
| `CatalogTimeTest` | Unitaria | Hora de las sesiones interpretada en `CATALOG_TIME_ZONE` |
| `Componente2ReservasApplicationTests` | Arranque | El contexto arranca con el perfil `test` |

Para pruebas manuales sin C1 ni C3 está el perfil `isolated` con WireMock:
ver [docs/ejecucion-aislada.md](docs/ejecucion-aislada.md).

---

## 13. Decisiones y limitaciones conocidas

- **Database per service:** C2 nunca lee Mongo de C1 ni MySQL de C3; todo pasa
  por HTTP.
- **Outbox:** evita perder eventos si el broker cae entre el pago y la
  publicación.
- **Sin seed:** la base arranca vacía; las reservas se crean con la aplicación.
- **Tests sobre H2, no sobre PostgreSQL:** H2 en modo PostgreSQL respeta la
  restricción `UNIQUE` de butacas, pero no es idéntico a PostgreSQL. Para validar
  contra la base real hay que usar el entorno aislado o el compose.
- **Check-in:** valida el código de la entrada y marca `checked_in_at`; no
  comprueba la hora de la sesión.
# Migración de IDs de Cartelera

Los identificadores de sesión, película y sala del Componente 1 se consumen y
almacenan como texto. Antes de desplegar esta versión sobre una base existente,
detén el servicio, haz un backup verificado y ejecuta
[`docs/migrations/001-catalog-ids-to-varchar.sql`](docs/migrations/001-catalog-ids-to-varchar.sql)
en PostgreSQL. Los valores históricos se conservan como cadenas decimales; la
migración no reasigna IDs.
