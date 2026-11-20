# Componente 1 — Catálogo y Cartelera

Microservicio dueño del **catálogo** (cines, películas y salas) y de la
**programación** de sesiones. Es la fuente de verdad de todo lo que se puede
vender; el Componente 2 consulta aquí antes de permitir una reserva.

- **Tecnología:** Java 21 + Spring Boot 4
- **Puerto:** `8081`
- **Ruta base:** `/api/v1`
- **Base de datos:** MongoDB (`cine_catalog_db`)
- **Colecciones:** `cinemas`, `movies`, `rooms`, `sessions`

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
└── services/     CinemaService, MovieService, RoomService, SessionService
```

Los IDs de cines, películas y salas son UUID en formato texto; los IDs de
sesiones son ULID de 26 caracteres. Sus referencias (`cinemaId`, `movieId` y
`roomId`) también se almacenan como texto.

---

## 3. Puesta en marcha

### Con Docker

```bash
docker compose up -d --build
```

El arranque del servicio ejecuta `CatalogSeeder` cuando
`app.seed.enabled=true`. La carga es idempotente y crea únicamente los
identificadores que aún no existen. Puede desactivarse con
`APP_SEED_ENABLED=false`. Los pósteres se sirven desde
`http://localhost:8080/media/posters/<archivo>.jpg`.

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

Las pruebas se ejecutan sin necesidad de MongoDB en marcha (los
repositorios se simulan con Mockito):

| Test | Qué cubre |
|---|---|
| `CatalogServicesTest` | Reglas de negocio: filtros de cartelera, capacidad de salas, cine inexistente, solapamientos, normalización de género y edad, cambio automático de estado de las sesiones |
| `MovieControllerIntegrationTest` | Seguridad y contrato HTTP de `/movies` con `@WebMvcTest` |
| `CarteleraAcceptanceTest` | Flujo de catálogo, sesiones por lote, roles de salas e integridad de borrado con `MockMvc` |
| `Componente1CarteleraApplicationTests` | Arranque del contexto |

`CarteleraAcceptanceTest` simula `CinemaRepository`, `MovieRepository`,
`RoomRepository` y `SessionRepository` con `@MockitoBean`, por eso no necesita
MongoDB.

---

## 4. Endpoints

Ruta base: `http://localhost:8081/api/v1`.

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| `GET` | `/cinemas` | Público | Lista cines (`city`) |
| `GET` | `/cinemas/{cinemaId}` | Público | Detalle del cine |
| `POST` | `/cinemas` | JWT válido | Crea cine |
| `PUT` | `/cinemas/{cinemaId}` | JWT válido | Actualiza cine |
| `DELETE` | `/cinemas/{cinemaId}` | JWT válido | Borra cine (409 si tiene salas) |
| `GET` | `/cinemas/{cinemaId}/rooms` | Público | Salas de un cine (404 si el cine no existe) |
| `GET` | `/movies` | Público | Lista películas (`genre`, `inShowcase`) |
| `GET` | `/movies/{movieId}` | Público | Detalle |
| `POST` | `/movies` | JWT válido | Crea película |
| `PUT` | `/movies/{movieId}` | JWT válido | Actualiza |
| `DELETE` | `/movies/{movieId}` | JWT válido | Borra (409 si tiene sesiones) |
| `GET` | `/rooms` | JWT válido | Lista salas; filtro opcional `cinemaId` |
| `GET` | `/rooms/{roomId}` | JWT válido | Detalle de sala |
| `POST` | `/rooms` | JWT válido | Crea sala (requiere `cinemaId` existente) |
| `PUT` | `/rooms/{roomId}` | ADMIN | Actualiza sala y recalcula aforo |
| `DELETE` | `/rooms/{roomId}` | ADMIN | Borra sala (409 si tiene sesiones futuras no canceladas) |
| `GET` | `/sessions` | Público | Lista sesiones (`date`, `movieId`) |
| `GET` | `/sessions/{sessionId}` | JWT válido | Detalle de sesión |
| `POST` | `/sessions` | JWT válido | Programa una sesión |
| `POST` | `/sessions/batch` | JWT válido | Programa sesiones por días de la semana en un rango inclusivo |
| `PATCH` | `/sessions/{sessionId}` | JWT válido | Cambia estado, sala u hora |

> `GET /rooms` **no** es público: el Componente 2 lo llama propagando el JWT del
> usuario, y el Componente 4 también. En cambio, `GET /cinemas/**` sí es público.

Ejemplos:

```bash
# Cartelera pública
curl "http://localhost:8081/api/v1/cinemas?city=Madrid"
curl "http://localhost:8081/api/v1/cinemas/550e8400-e29b-41d4-a716-446655440000/rooms"
curl "http://localhost:8081/api/v1/movies?inShowcase=true"
curl "http://localhost:8081/api/v1/sessions?date=2026-11-18"

# Crear una sala dentro de un cine (necesita JWT)
curl -X POST http://localhost:8081/api/v1/rooms \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"cinemaId":"550e8400-e29b-41d4-a716-446655440000","name":"Sala 4","roomType":"ESTANDAR_2D","rowsCount":10,"columnsCount":12}'

# Poner una sesión a la venta (necesario para que el C2 permita reservar)
curl -X PATCH http://localhost:8081/api/v1/sessions/01ARZ3NDEKTSV4RRFFQ69G5FAV \
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

- `genre` se guarda **normalizado** al crear o editar: en mayúsculas, sin tildes
  y con `_` en vez de espacios (`"Ciencia ficción"` → `CIENCIA_FICCION`). Así
  coincide con los géneros favoritos de los usuarios (C3) que usan las
  recomendaciones (C4). El filtro `GET /movies?genre=` aplica la misma regla.
- `ageRating`: `TP`, `+7`, `+12`, `+16` o `+18`. Un `+0` se guarda como `TP`.
- Al arrancar, el seed corrige las películas que ya existían con los valores
  antiguos (géneros con tilde y `+0`).

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
   **409**. `POST /sessions/batch` crea las fechas compatibles y devuelve por
   separado las creadas y las que chocaron.
2. **Estado inicial:** toda sesión nueva nace en `PROGRAMADA`. El Componente 2
   **solo permite reservar** sesiones en `A_LA_VENTA` que no hayan empezado, así
   que hay que hacer `PATCH` antes de vender.
3. **Cambio automático de estado:** una tarea programada (`SessionStatusScheduler`,
   cada `SESSION_STATUS_INTERVAL`, por defecto 60 s) pasa a `EN_CURSO` las sesiones
   `PROGRAMADA` o `A_LA_VENTA` cuya hora de inicio ha llegado, y a `FINALIZADA` las
   `EN_CURSO` cuya hora de fin ha pasado. Las `CANCELADA` no se tocan. Si el admin
   pone una sesión `EN_CURSO` a mano, su `endTime` se recalcula como *ahora +
   duración + 20 min* y se finaliza a esa hora. "Ahora" se calcula en la zona
   `CATALOG_TIME_ZONE` (`Europe/Madrid`), porque las horas de las sesiones son
   hora local sin zona. Se desactiva con `APP_SCHEDULING_ENABLED=false`.
4. **En cartelera:** `GET /movies?inShowcase=true` devuelve las películas con
   alguna sesión no cancelada cuyo `endTime` aún no ha pasado.
5. **Integridad referencial:** al crear una sala se comprueba que exista su
   `cinemaId`; al crear una sesión se comprueba que existan la película y la sala.
6. **Cine → salas:** `GET /cinemas/{cinemaId}/rooms` valida primero el cine y
   devuelve **404** si no existe. `GET /rooms?cinemaId=` hace el mismo filtro sin
   validar el cine (devuelve lista vacía si no hay salas).
7. **Integridad al borrar:** `DELETE /movies/{movieId}` devuelve **409** si
   existe cualquier sesión asociada (también pasada o cancelada), y
   `DELETE /cinemas/{cinemaId}` devuelve **409** si tiene salas. No se borran
   entidades en cascada ni se dejan referencias colgantes.

---

## 7. Seguridad

Es un *OAuth2 Resource Server*: valida los JWT RS256 del Componente 3 con el
JWKS (`JWT_JWK_SET_URI`) y el emisor (`JWT_ISSUER`).

- Lecturas de `cinemas`, `movies` y `sessions`: públicas.
- Lecturas de `rooms` y las escrituras salvo las indicadas abajo: cualquier JWT válido.
- `PUT` y `DELETE /rooms/{roomId}`: solo usuarios con rol `ADMIN`.

> **Limitación conocida:** las demás escrituras todavía no comprueban rol.
> Cualquier token válido puede usarlas; convendría limitar las operaciones
> administrativas del catálogo a `ADMIN`.

---

## 8. IDs y base de datos

- Los IDs nuevos de `Cinema`, `Movie` y `Room` se crean con
  `UUID.randomUUID().toString()`; las sesiones crean ULIDs monotónicos con
  `ulid-creator` al construir el documento.
- C1 ejecuta `CatalogSeeder` automáticamente al arrancar, salvo que
  `APP_SEED_ENABLED=false`. La carga es idempotente: conserva registros
  existentes y crea únicamente documentos con IDs que todavía no existen.
  Puede cargarse también manualmente con
  [`scripts/seed-catalog.mongodb.js`](scripts/seed-catalog.mongodb.js).
- El seed actual carga 7 cines, 8 películas, 12 salas y 278 sesiones en
  `A_LA_VENTA` para los **14 días** siguientes al primer arranque, para que haya
  cartelera aunque se pruebe días después. Los 3 primeros días tienen la
  programación base (48 sesiones: 18 en Madrid, 8 en Barcelona, 6 en Cáceres,
  3 en Navatrasierra, 3 en Madroñera, 7 en Málaga y 3 en Cuenca); del día 4 al 14
  se repiten los horarios del día 1 y del día 2. Es idempotente, no modifica
  documentos existentes y no crea una sesión que choque con otra de la misma sala. Los detalles están en
  [`docs/seed-cartelera.md`](docs/seed-cartelera.md).
- Para actualizar las bases relacionales de C2–C4, aplica sus scripts antes de
  arrancar las versiones nuevas:
  [C2 PostgreSQL](../componente2-reservas/docs/migrations/001-catalog-ids-to-varchar.sql),
  [C3 MySQL](../componente3-usuarios/docs/migrations/001-catalog-ids-to-varchar.sql)
  y [C4 PostgreSQL](../componente4-analitica/docs/migrations/001-catalog-ids-to-varchar.sql).
- **IDs externos:** los identificadores de cine, película, sala y sesión en
  C2–C4 también son texto; cada consumidor documenta su propia migración.
- **Sin `distributorId`:** el merge eliminó `Movie.distributor`; C4 conserva el
  campo `distributor` en su DTO/evento, pero llega `null`. Si se necesita el
  filtro, hay que reintroducir la distribuidora (idealmente con id) en C1.

---

## 9. Documentación relacionada

- [`docs/README.md`](docs/README.md) — explicación del componente con esquemas.
- [`docs/ejecucion-y-pruebas.md`](docs/ejecucion-y-pruebas.md) — ejecución y pruebas.
- [`docs/seed-cartelera.md`](docs/seed-cartelera.md) — datos y decisiones para llenar la base.
