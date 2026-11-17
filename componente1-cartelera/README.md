# Componente 1 — Catálogo y Cartelera

Microservicio dueño del **catálogo** (películas y salas) y de la
**programación** de sesiones. Es la fuente de verdad de todo lo que se puede
vender; el Componente 2 consulta aquí antes de permitir una reserva.

- **Tecnología:** Java 21 + Spring Boot 4
- **Puerto:** `8081`
- **Ruta base:** `/api/v1`
- **Base de datos:** MongoDB (`cine_catalog_db`)
- **Colecciones:** `movies`, `rooms`, `sessions`

---

## 1. Qué hace

| Área | Responsabilidad |
|---|---|
| Películas | CRUD del catálogo, filtro por género y por "en cartelera" |
| Salas | Alta y consulta de salas con aforo, tipo y precio base |
| Sesiones | Programación de pases con validación de solapamiento |
| Estados | Ciclo de vida del pase: `PROGRAMADA` → `A_LA_VENTA` → `EN_CURSO` → `FINALIZADA` / `CANCELADA` |

Es un servicio de **solo catálogo**: no sabe nada de butacas vendidas ni de
pagos. Eso pertenece al Componente 2.

---

## 2. Arquitectura interna

```
com.cinasee.componente1_cartelera
├── config/       SecurityConfig (OAuth2 Resource Server)
├── controllers/  MovieController, RoomController, SessionController
├── models/       Documentos Mongo + requests de creación/actualización
├── repositories/ MovieRepository, RoomRepository, SessionRepository
└── services/     MovieService, RoomService, SessionService
```

Cada documento usa un `Long id` propio como `@Id` (no el `ObjectId` de Mongo).

---

## 3. Puesta en marcha

### Con Docker

```bash
docker compose up -d --build
```

### En local

```bash
docker compose up -d mongodb-cartelera componente3-usuarios
cd componente1-cartelera
./mvnw spring-boot:run
```

Necesita el Componente 3 para descargar el JWKS y validar tokens.

### Tests

```bash
cd componente1-cartelera
./mvnw test
```

Cuatro clases, todas en verde sin necesidad de MongoDB en marcha (los
repositorios se simulan con Mockito):

| Test | Qué cubre |
|---|---|
| `CatalogServicesTest` | Reglas de negocio: filtros de cartelera, capacidad de salas, solapamientos |
| `MovieControllerIntegrationTest` | Seguridad y contrato HTTP de `/movies` con `@WebMvcTest` |
| `CarteleraAcceptanceTest` | Flujo completo película → sala → sesión con `MockMvc` |
| `Componente1CarteleraApplicationTests` | Arranque del contexto |

---

## 4. Endpoints

Ruta base: `http://localhost:8081/api/v1`.

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| `GET` | `/movies` | Público | Lista películas (`genre`, `inShowcase`) |
| `GET` | `/movies/{movieId}` | Público | Detalle |
| `POST` | `/movies` | JWT válido | Crea película |
| `PUT` | `/movies/{movieId}` | JWT válido | Actualiza |
| `DELETE` | `/movies/{movieId}` | JWT válido | Borra |
| `GET` | `/rooms` | JWT válido | Lista salas |
| `GET` | `/rooms/{roomId}` | JWT válido | Detalle de sala |
| `POST` | `/rooms` | JWT válido | Crea sala |
| `GET` | `/sessions` | Público | Lista sesiones (`date`, `movieId`) |
| `GET` | `/sessions/{sessionId}` | JWT válido | Detalle de sesión |
| `POST` | `/sessions` | JWT válido | Programa una sesión |
| `PATCH` | `/sessions/{sessionId}` | JWT válido | Cambia estado, sala u hora |

> `GET /rooms` **no** es público: el Componente 2 lo llama propagando el JWT del
> usuario, y el Componente 4 también.

Ejemplos:

```bash
# Cartelera pública
curl "http://localhost:8081/api/v1/movies?inShowcase=true"
curl "http://localhost:8081/api/v1/sessions?date=2026-11-18"

# Poner una sesión a la venta (necesario para que el C2 permita reservar)
curl -X PATCH http://localhost:8081/api/v1/sessions/3001 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status":"A_LA_VENTA"}'
```

---

## 5. Modelo de datos

### `Movie`
`id`, `title`, `synopsis`, `durationMinutes`, `genre`, `ageRating`,
`distributor`, `posterUrl`, `trailerUrl`.

### `Room`
`id`, `name`, `roomType` (`ESTANDAR_2D`, `DIGITAL_3D`, `IMAX`, `VIP`, `4DX`),
`capacity`, `rowsCount`, `columnsCount`, `wheelchairAccessible`, `basePrice`.

`capacity` **no** se recibe del cliente: se calcula como
`rowsCount * columnsCount`.

### `Session`
`id`, `movieId`, `roomId`, `startTime`, `endTime`, `status`, `ticketPrice`.

`endTime` se calcula automáticamente:
`startTime + duración de la película + 20 minutos de preparación de sala`.

---

## 6. Reglas de negocio

1. **Solapamiento:** una sala no puede tener dos sesiones cuyos intervalos
   (incluida la preparación) se crucen. Si ocurre, `POST /sessions` devuelve
   **409**.
2. **Estado inicial:** toda sesión nueva nace en `PROGRAMADA`. El Componente 2
   **solo permite reservar** sesiones en `A_LA_VENTA` que no hayan empezado, así
   que hay que hacer `PATCH` antes de vender.
3. **En cartelera:** `GET /movies?inShowcase=true` devuelve las películas con
   alguna sesión no cancelada cuyo `endTime` aún no ha pasado.
4. **Integridad referencial:** al crear una sesión se comprueba que existan la
   película y la sala.
5. **Borrado de películas:** no comprueba si tiene sesiones asociadas; hay que
   evitarlo desde el cliente.

---

## 7. Seguridad

Es un *OAuth2 Resource Server*: valida los JWT RS256 del Componente 3 con el
JWKS (`JWT_JWK_SET_URI`) y el emisor (`JWT_ISSUER`).

- Lecturas de `movies` y `sessions`: públicas.
- Lecturas de `rooms` y todas las escrituras: cualquier JWT válido.

> **Limitación conocida:** las escrituras no comprueban rol. Cualquier token
> válido (incluido uno de invitado) puede crear, editar o borrar catálogo. Lo
> correcto sería exigir `ADMIN` o `STAFF_TAQUILLA`.

---

## 8. Decisiones y trabajo pendiente

- **IDs por `System.currentTimeMillis()`:** `MovieService`, `RoomService` y
  `SessionService` generan el id con la marca de tiempo actual. Dos altas en el
  mismo milisegundo colisionan y los ids no son reproducibles entre entornos.
  Debería sustituirse por una secuencia atómica o por ids fijos en el seed.
- **Sin seed de cartelera:** la base arranca vacía y Mongo crea las colecciones
  al primer `POST`. El sistema no se puede demostrar de punta a punta sin datos.
  La lista concreta de películas, salas y sesiones está en
  [`docs/seed-cartelera.md`](docs/seed-cartelera.md).
- **Sin `cinemaId`:** el modelo no distingue cines. El Componente 3 usa
  `cinemaId = 1` y el Componente 4 lo acepta pero no lo filtra.
- **Sin `distributorId`:** `Movie.distributor` es texto (`"Warner Bros."`), así
  que el Componente 4 no puede aplicar su filtro `distributorId`. Si se
  necesita, hay que añadir `distributorId` al modelo, al `CreateMovieRequest` y
  al seed.

---

## 9. Documentación relacionada

- [`docs/README.md`](docs/README.md) — notas originales del componente.
- [`docs/ejecucion-y-pruebas.md`](docs/ejecucion-y-pruebas.md) — ejecución y pruebas.
- [`docs/seed-cartelera.md`](docs/seed-cartelera.md) — datos y decisiones para llenar la base.
