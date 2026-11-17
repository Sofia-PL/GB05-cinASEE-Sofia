# Componente 1 — Catálogo y Cartelera

Microservicio dueño del **catálogo** (cines, películas y salas) y de la
**programación** de sesiones. Es la fuente de verdad de todo lo que se puede
vender; el Componente 2 consulta aquí antes de permitir una reserva.

- **Tecnología:** Java 21 + Spring Boot 4
- **Puerto:** `8081`
- **Ruta base:** `/api/v1`
- **Base de datos:** MongoDB (`cine_catalog_db`)
- **Colecciones:** `cinemas`, `movies`, `rooms`, `sessions` (+ `id_counters`)

---

## 1. Qué hace

| Área | Responsabilidad |
|---|---|
| Cines | CRUD de cines con nombre, dirección, ciudad y teléfono; filtro por ciudad |
| Películas | CRUD del catálogo, filtro por género y por "en cartelera" |
| Salas | Alta y consulta de salas **vinculadas a un cine**, con aforo, tipo y precio base |
| Sesiones | Programación de pases con validación de solapamiento |
| Estados | Ciclo de vida del pase: `PROGRAMADA` → `A_LA_VENTA` → `EN_CURSO` → `FINALIZADA` / `CANCELADA` |

Es un servicio de **solo catálogo**: no sabe nada de butacas vendidas ni de
pagos. Eso pertenece al Componente 2.

El modelo es multicine: un `Cinema` tiene muchas `Room`, y cada `Session`
proyecta una `Movie` en una `Room` concreta.

---

## 2. Arquitectura interna

```
com.cinasee.componente1_cartelera
├── config/       SecurityConfig (OAuth2 Resource Server)
├── controllers/  CinemaController, MovieController, RoomController, SessionController
├── models/       Documentos Mongo (Cinema, Movie, Room, Session) + requests
├── repositories/ CinemaRepository, MovieRepository, RoomRepository, SessionRepository
└── services/     CinemaService, MovieService, RoomService, SessionService,
                  SessionIdGenerator (contador atómico de sesiones)
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
| `CatalogServicesTest` | Reglas de negocio: filtros de cartelera, capacidad de salas, cine inexistente, solapamientos |
| `MovieControllerIntegrationTest` | Seguridad y contrato HTTP de `/movies` con `@WebMvcTest` |
| `CarteleraAcceptanceTest` | Flujo completo cine → película → sala → sesión con `MockMvc` |
| `Componente1CarteleraApplicationTests` | Arranque del contexto |

`CarteleraAcceptanceTest` simula `CinemaRepository`, `MovieRepository`,
`RoomRepository`, `SessionRepository` y `SessionIdGenerator` con `@MockitoBean`,
por eso no necesita MongoDB.

---

## 4. Endpoints

Ruta base: `http://localhost:8081/api/v1`.

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| `GET` | `/cinemas` | Público | Lista cines (`city`) |
| `GET` | `/cinemas/{cinemaId}` | Público | Detalle del cine |
| `POST` | `/cinemas` | JWT válido | Crea cine |
| `PUT` | `/cinemas/{cinemaId}` | JWT válido | Actualiza cine |
| `DELETE` | `/cinemas/{cinemaId}` | JWT válido | Borra cine |
| `GET` | `/cinemas/{cinemaId}/rooms` | Público | Salas de un cine (404 si el cine no existe) |
| `GET` | `/movies` | Público | Lista películas (`genre`, `inShowcase`) |
| `GET` | `/movies/{movieId}` | Público | Detalle |
| `POST` | `/movies` | JWT válido | Crea película |
| `PUT` | `/movies/{movieId}` | JWT válido | Actualiza |
| `DELETE` | `/movies/{movieId}` | JWT válido | Borra |
| `GET` | `/rooms` | JWT válido | Lista salas; filtro opcional `cinemaId` |
| `GET` | `/rooms/{roomId}` | JWT válido | Detalle de sala |
| `POST` | `/rooms` | JWT válido | Crea sala (requiere `cinemaId` existente) |
| `GET` | `/sessions` | Público | Lista sesiones (`date`, `movieId`) |
| `GET` | `/sessions/{sessionId}` | JWT válido | Detalle de sesión |
| `POST` | `/sessions` | JWT válido | Programa una sesión |
| `PATCH` | `/sessions/{sessionId}` | JWT válido | Cambia estado, sala u hora |

> `GET /rooms` **no** es público: el Componente 2 lo llama propagando el JWT del
> usuario, y el Componente 4 también. En cambio, `GET /cinemas/**` sí es público.

Ejemplos:

```bash
# Cartelera pública
curl "http://localhost:8081/api/v1/cinemas?city=Madrid"
curl "http://localhost:8081/api/v1/cinemas/1/rooms"
curl "http://localhost:8081/api/v1/movies?inShowcase=true"
curl "http://localhost:8081/api/v1/sessions?date=2026-11-18"

# Crear una sala dentro de un cine (necesita JWT)
curl -X POST http://localhost:8081/api/v1/rooms \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"cinemaId":1,"name":"Sala 4","roomType":"ESTANDAR_2D","rowsCount":10,"columnsCount":12}'

# Poner una sesión a la venta (necesario para que el C2 permita reservar)
curl -X PATCH http://localhost:8081/api/v1/sessions/3001 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status":"A_LA_VENTA"}'
```

---

## 5. Modelo de datos

### `Cinema`
`id`, `name`, `address`, `city`, `phone`.

### `Movie`
`id`, `title`, `synopsis`, `durationMinutes`, `genre`, `ageRating`,
`posterUrl`, `trailerUrl`.

> La distribuidora **ya no** forma parte del modelo: el merge eliminó
> `Movie.distributor`. Una película es global y puede proyectarse en varios cines.

### `Room`
`id`, `cinemaId`, `name`, `roomType` (`ESTANDAR_2D`, `DIGITAL_3D`, `IMAX`, `VIP`, `4DX`),
`capacity`, `rowsCount`, `columnsCount`, `wheelchairAccessible`, `basePrice`.

- `cinemaId` es obligatorio y debe apuntar a un `Cinema` existente (`404` si no).
- `capacity` **no** se recibe del cliente: se calcula como
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
4. **Integridad referencial:** al crear una sala se comprueba que exista su
   `cinemaId`; al crear una sesión se comprueba que existan la película y la sala.
5. **Cine → salas:** `GET /cinemas/{cinemaId}/rooms` valida primero el cine y
   devuelve **404** si no existe. `GET /rooms?cinemaId=` hace el mismo filtro sin
   validar el cine (devuelve lista vacía si no hay salas).
6. **Borrado de películas:** no comprueba si tiene sesiones asociadas; hay que
   evitarlo desde el cliente. Lo mismo ocurre al borrar un cine con salas.

---

## 7. Seguridad

Es un *OAuth2 Resource Server*: valida los JWT RS256 del Componente 3 con el
JWKS (`JWT_JWK_SET_URI`) y el emisor (`JWT_ISSUER`).

- Lecturas de `cinemas`, `movies` y `sessions`: públicas.
- Lecturas de `rooms` y todas las escrituras: cualquier JWT válido.

> **Limitación conocida:** las escrituras no comprueban rol. Cualquier token
> válido (incluido uno de invitado) puede crear, editar o borrar catálogo. Lo
> correcto sería exigir `ADMIN` o `STAFF_TAQUILLA`.

---

## 8. Decisiones y trabajo pendiente

- **IDs:** las **sesiones** usan `SessionIdGenerator`, un contador atómico en
  Mongo (`id_counters/_id="sessions"`) que evita colisiones. `MovieService`,
  `RoomService` y `CinemaService` siguen usando `System.currentTimeMillis()`:
  dos altas en el mismo milisegundo colisionan y los ids no son reproducibles
  entre entornos. Lo ideal es extender el contador al resto de entidades.
- **Contador de sesiones empieza en 1:** si se siembran sesiones con ids
  `3001+`, hay que inicializar el contador en `3000`
  (`db.id_counters.insertOne({_id:"sessions", value:3000})`) para que las
  sesiones creadas por API no acaben colisionando al llegar a ese rango.
- **Sin seed de cartelera:** la base arranca vacía y Mongo crea las colecciones
  al primer `POST`. El sistema no se puede demostrar de punta a punta sin datos.
  La lista concreta de cines, películas, salas y sesiones está en
  [`docs/seed-cartelera.md`](docs/seed-cartelera.md).
- **Cines modelados; C4 ya los aprovecha, C2/C3 todavía no:** C1 tiene `Cinema`
  y `Room.cinemaId`. C4 filtra la ocupación por cine cruzando
  `GET /rooms?cinemaId=` con la venta por `roomId` de C2. En cambio, el
  `cinemaId` de C3 sigue siendo un número libre que no se valida contra C1 y C2
  trabaja por `sessionId`/`roomId` sin conocer el cine. Para el filtro de punta a
  punta habría que propagar `cinemaId` a C2 y validarlo en C3.
- **Sin `distributorId`:** el merge eliminó `Movie.distributor`; C4 conserva el
  campo `distributor` en su DTO/evento, pero llega `null`. Si se necesita el
  filtro, hay que reintroducir la distribuidora (idealmente con id) en C1.

---

## 9. Documentación relacionada

- [`docs/README.md`](docs/README.md) — notas originales del componente.
- [`docs/ejecucion-y-pruebas.md`](docs/ejecucion-y-pruebas.md) — ejecución y pruebas.
- [`docs/seed-cartelera.md`](docs/seed-cartelera.md) — datos y decisiones para llenar la base.
