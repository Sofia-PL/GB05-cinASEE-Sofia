# Seed de cartelera — datos y decisiones para llenar la base del Componente 1

Este documento es la lista de trabajo para completar el Componente 1. Al
terminarlo, el sistema se podrá demostrar de punta a punta: cartelera → reserva
→ pago → evento → analítica.

El Componente 1 hoy **arranca con MongoDB vacío**. Este seed crea cines,
películas, salas y sesiones con ids fijos y fechas relativas al día de arranque.

---

## 1. Decisiones que hay que tomar antes de escribir código

### 1.1 IDs de texto

La API crea IDs UUID para cines, películas y salas, y ULID para sesiones. Un
seed determinista puede usar IDs de texto fijos; los valores numéricos de las
tablas siguientes deben persistirse como cadenas (por ejemplo, `"1001"`).
Esto conserva relaciones estables para una demo y no cambia cómo se generan
IDs de altas normales.

La base de C1 debe estar vacía al iniciar esta versión. Si contiene registros
del esquema anterior, elimina las colecciones antes de cargar el seed; no se
conservan ni migran esos datos.

Para el seed se reservan rangos de texto:

| Entidad | Rango | Ejemplo |
|---|---|---|
| Cines | `"1"` – `"999"` | `"1"` |
| Películas | `"1001"` – `"1999"` | `"1001"` |
| Salas | `"2001"` – `"2999"` | `"2001"` |
| Sesiones | `"3001"` – `"3999"` | `"3001"` |

El seed escribe directamente con los repositorios y puede asignar sus IDs de
texto fijos. El servicio API genera UUID/ULID para las altas nuevas.

### 1.2 Seed idempotente y desactivable

Mismo patrón que el Componente 3 (`DemoDataInitializer`):

- `implements CommandLineRunner`
- `@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)`
- antes de guardar, `if (repository.existsById(id)) return;`

Así se puede arrancar N veces sin duplicar datos y desactivar en producción con
`APP_SEED_ENABLED=false`.

### 1.3 Fechas relativas

Las sesiones se generan con `LocalDate.now().plusDays(1..3)`. Si se usaran
fechas fijas, la cartelera caducaría y `inShowcase=true` devolvería vacío.

### 1.4 Todas las sesiones en `A_LA_VENTA`

El Componente 2 rechaza reservar si la sesión no está en `A_LA_VENTA` y ya ha
empezado:

```java
// ReservationService.ensureSessionOnSale
if (!CatalogSession.ON_SALE.equals(session.status())) { ... }
```

El seed debe crear las sesiones directamente en `A_LA_VENTA` (no en
`PROGRAMADA`).

### 1.5 Capacidad y precios coherentes

- `capacity = rowsCount * columnsCount` (la calcula `RoomService`, pero el seed
  la escribe a mano: **debe coincidir**).
- `ticketPrice` de la sesión debe ser igual o superior al `basePrice` de la sala.
- Moneda: **EUR** en todo el sistema.

### 1.6 Zona horaria

`Session.startTime` es un `LocalDateTime` sin zona. El Componente 2 lo
interpreta con `CATALOG_TIME_ZONE` (por defecto `UTC`). Si se siembra en hora
local de Madrid y el Componente 2 cree que es UTC, las sesiones aparecerán una
hora desplazadas.

**Recomendación:** para desarrollo/demo, fijar en el Componente 2:

```yaml
CATALOG_TIME_ZONE: Europe/Madrid
```

y sembrar en hora local española (las horas de las tablas de abajo).

### 1.7 No se puede sembrar por API con ids fijos

Los `CreateMovieRequest`, `CreateRoomRequest` y `CreateSessionRequest` **no**
tienen campo `id`. Si se siembra con `curl`, los ids saldrán del timestamp y no
coincidirán con esta lista.

Dos opciones:

1. **Recomendada:** `CommandLineRunner` que escribe con los repositorios.
2. Alternativa: añadir `id` opcional a los requests y usarlo en los servicios si
   viene informado.

---

## 2. Películas (colección `movies`)

| id | title | genre | durationMinutes | ageRating |
|---|---|---|---|---|
| 1001 | Dune: Parte Tres | CIENCIA_FICCION | 165 | +12 |
| 1002 | Oppenheimer | DRAMA | 180 | +16 |
| 1003 | Barbie | COMEDIA | 114 | +7 |
| 1004 | Spider-Man: Across the Spider-Verse | ANIMACION | 140 | +7 |
| 1005 | El Padrino | DRAMA | 175 | +18 |
| 1006 | Interstellar | CIENCIA_FICCION | 169 | +12 |
| 1007 | Coco | ANIMACION | 105 | +0 |
| 1008 | John Wick 4 | ACCION | 169 | +18 |

Campos adicionales:

- `synopsis`: una frase por película.
- `posterUrl`: puede quedar `null` o apuntar a una imagen pública.
- `trailerUrl`: puede quedar `null`.

> La entidad `Movie` **no tiene distribuidora**: el merge eliminó
> `Movie.distributor`. No la añadas al seed; C4 mostrará `distributor = null`.

> Los géneros deben escribirse **en mayúsculas y sin tildes** (`ANIMACION`, no
> `Animación`): el Componente 4 compara géneros con `toUpperCase()`.

---

## 3. Cines (colección `cinemas`)

| id | name | address | city | phone |
|---|---|---|---|---|
| 1 | CinAsee Madrid | Gran Vía 1 | Madrid | 910000001 |
| 2 | CinAsee Barcelona | Passeig de Gràcia 2 | Barcelona | 930000002 |

> El cine `"1"` debe existir porque el seed de C3 asigna `cinemaId = "1"` al staff
> (`staff@cinasee.com`). Si se cambia ese id, hay que cambiar también
> `APP_SEED_STAFF_CINEMA_ID` en C3.

---

## 4. Salas (colección `rooms`)

| id | cinemaId | name | roomType | rowsCount | columnsCount | capacity | basePrice | wheelchairAccessible |
|---|---|---|---|---|---|---|---|---|
| 2001 | 1 | Sala 1 - Estándar | ESTANDAR_2D | 10 | 12 | 120 | 8.50 | true |
| 2002 | 1 | Sala 2 - 3D | DIGITAL_3D | 8 | 10 | 80 | 10.50 | true |
| 2003 | 1 | Sala 3 - VIP | VIP | 6 | 8 | 48 | 14.00 | true |
| 2004 | 2 | Sala 1 - Estándar | ESTANDAR_2D | 10 | 12 | 120 | 8.50 | true |
| 2005 | 2 | Sala 2 - 3D | DIGITAL_3D | 8 | 10 | 80 | 10.50 | true |

- `cinemaId` es obligatorio: el seed debe crear los cines **antes** que las salas.
- La capacidad es la que usará el Componente 4 para calcular ocupación y la que
  usa el Componente 2 para validar que las butacas existen en el mapa.
- Las sesiones de ejemplo usan solo las salas del cine `1`; las del cine `2`
  quedan libres para crear sesiones por API.

---

## 5. Sesiones (colección `sessions`)

Horas en **hora local española** (ver decisión 1.6). La columna `fin` ya incluye
los 20 minutos de preparación de sala.

### Día 1 — `LocalDate.now().plusDays(1)`

| id | movieId | roomId | inicio | fin | ticketPrice |
|---|---|---|---|---|---|
| 3001 | 1001 | 2001 | 16:00 | 19:05 | 8.50 |
| 3002 | 1008 | 2001 | 19:30 | 22:39 | 8.50 |
| 3003 | 1004 | 2002 | 17:00 | 19:40 | 10.50 |
| 3004 | 1006 | 2002 | 20:00 | 23:09 | 10.50 |
| 3005 | 1003 | 2003 | 17:30 | 19:44 | 14.00 |
| 3006 | 1002 | 2003 | 20:00 | 23:20 | 14.00 |

### Día 2 — `LocalDate.now().plusDays(2)`

| id | movieId | roomId | inicio | fin | ticketPrice |
|---|---|---|---|---|---|
| 3007 | 1005 | 2001 | 16:00 | 19:15 | 8.50 |
| 3008 | 1007 | 2001 | 19:45 | 21:50 | 8.50 |
| 3009 | 1008 | 2002 | 17:00 | 20:09 | 10.50 |
| 3010 | 1001 | 2002 | 20:30 | 23:35 | 10.50 |
| 3011 | 1002 | 2003 | 17:30 | 20:50 | 14.00 |
| 3012 | 1003 | 2003 | 21:00 | 23:14 | 14.00 |

### Día 3 — `LocalDate.now().plusDays(3)`

| id | movieId | roomId | inicio | fin | ticketPrice |
|---|---|---|---|---|---|
| 3013 | 1006 | 2001 | 16:00 | 19:09 | 8.50 |
| 3014 | 1004 | 2001 | 19:30 | 22:10 | 8.50 |
| 3015 | 1007 | 2002 | 17:00 | 18:45 | 10.50 |
| 3016 | 1005 | 2002 | 19:15 | 22:50 | 10.50 |
| 3017 | 1001 | 2003 | 17:30 | 20:35 | 14.00 |
| 3018 | 1008 | 2003 | 21:00 | 00:09 (+1) | 14.00 |

**Todas con `status = "A_LA_VENTA"`.**

Notas:

- La sesión `3018` termina pasada la medianoche; es válido porque no hay otra
  sesión después en esa sala.
- Si se cambia una película por otra de mayor duración, hay que recalcular el
  `endTime` y comprobar que no invade la siguiente sesión.
- Los ids de sesión no se reutilizan entre días para que las reservas del
  Componente 2 apunten siempre a una sesión inequívoca.

---

## 6. Implementación propuesta

Nuevo archivo:
`componente1-cartelera/src/main/java/com/cinasee/componente1_cartelera/config/CatalogDataInitializer.java`

```java
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class CatalogDataInitializer implements CommandLineRunner {

    private static final int PREPARATION_MINUTES = 20;

    private final CinemaRepository cinemaRepository;
    private final MovieRepository movieRepository;
    private final RoomRepository roomRepository;
    private final SessionRepository sessionRepository;

    @Override
    public void run(String... args) {
        seedCinemas();
        seedMovies();
        seedRooms();
        seedSessions();
        log.info("Seed de cartelera comprobado: {} cines, {} películas, {} salas, {} sesiones",
                cinemaRepository.count(), movieRepository.count(),
                roomRepository.count(), sessionRepository.count());
    }

    private void seedCinemas() {
        cinema("1", "CinAsee Madrid", "Gran Vía 1", "Madrid", "910000001");
        cinema("2", "CinAsee Barcelona", "Passeig de Gràcia 2", "Barcelona", "930000002");
    }

    private void cinema(String id, String name, String address, String city, String phone) {
        if (cinemaRepository.existsById(id)) {
            return;
        }
        cinemaRepository.save(Cinema.builder()
                .id(id)
                .name(name)
                .address(address)
                .city(city)
                .phone(phone)
                .build());
    }

    private void seedMovies() {
        movie("1001", "Dune: Parte Tres", "CIENCIA_FICCION", 165, "+12");
        movie("1002", "Oppenheimer", "DRAMA", 180, "+16");
        movie("1003", "Barbie", "COMEDIA", 114, "+7");
        movie("1004", "Spider-Man: Across the Spider-Verse", "ANIMACION", 140, "+7");
        movie("1005", "El Padrino", "DRAMA", 175, "+18");
        movie("1006", "Interstellar", "CIENCIA_FICCION", 169, "+12");
        movie("1007", "Coco", "ANIMACION", 105, "+0");
        movie("1008", "John Wick 4", "ACCION", 169, "+18");
    }

    private void movie(String id, String title, String genre, int duration, String rating) {
        if (movieRepository.existsById(id)) {
            return;
        }
        movieRepository.save(Movie.builder()
                .id(id)
                .title(title)
                .synopsis(title + " — sinopsis de demostración.")
                .durationMinutes(duration)
                .genre(genre)
                .ageRating(rating)
                .build());
    }

    private void seedRooms() {
        room("2001", "1", "Sala 1 - Estándar", "ESTANDAR_2D", 10, 12, 8.50);
        room("2002", "1", "Sala 2 - 3D", "DIGITAL_3D", 8, 10, 10.50);
        room("2003", "1", "Sala 3 - VIP", "VIP", 6, 8, 14.00);
        room("2004", "2", "Sala 1 - Estándar", "ESTANDAR_2D", 10, 12, 8.50);
        room("2005", "2", "Sala 2 - 3D", "DIGITAL_3D", 8, 10, 10.50);
    }

    private void room(String id, String cinemaId, String name, String type,
                      int rows, int columns, double price) {
        if (roomRepository.existsById(id)) {
            return;
        }
        roomRepository.save(Room.builder()
                .id(id)
                .cinemaId(cinemaId)
                .name(name)
                .roomType(type)
                .rowsCount(rows)
                .columnsCount(columns)
                .capacity(rows * columns)
                .wheelchairAccessible(true)
                .basePrice(price)
                .build());
    }

    private void seedSessions() {
        LocalDate today = LocalDate.now();

        // Día 1
        session("3001", "1001", "2001", today.plusDays(1).atTime(16, 0), 8.50);
        session("3002", "1008", "2001", today.plusDays(1).atTime(19, 30), 8.50);
        session("3003", "1004", "2002", today.plusDays(1).atTime(17, 0), 10.50);
        session("3004", "1006", "2002", today.plusDays(1).atTime(20, 0), 10.50);
        session("3005", "1003", "2003", today.plusDays(1).atTime(17, 30), 14.00);
        session("3006", "1002", "2003", today.plusDays(1).atTime(20, 0), 14.00);

        // Día 2
        session("3007", "1005", "2001", today.plusDays(2).atTime(16, 0), 8.50);
        session("3008", "1007", "2001", today.plusDays(2).atTime(19, 45), 8.50);
        session("3009", "1008", "2002", today.plusDays(2).atTime(17, 0), 10.50);
        session("3010", "1001", "2002", today.plusDays(2).atTime(20, 30), 10.50);
        session("3011", "1002", "2003", today.plusDays(2).atTime(17, 30), 14.00);
        session("3012", "1003", "2003", today.plusDays(2).atTime(21, 0), 14.00);

        // Día 3
        session("3013", "1006", "2001", today.plusDays(3).atTime(16, 0), 8.50);
        session("3014", "1004", "2001", today.plusDays(3).atTime(19, 30), 8.50);
        session("3015", "1007", "2002", today.plusDays(3).atTime(17, 0), 10.50);
        session("3016", "1005", "2002", today.plusDays(3).atTime(19, 15), 10.50);
        session("3017", "1001", "2003", today.plusDays(3).atTime(17, 30), 14.00);
        session("3018", "1008", "2003", today.plusDays(3).atTime(21, 0), 14.00);
    }

    private void session(String id, String movieId, String roomId, LocalDateTime start, double price) {
        if (sessionRepository.existsById(id)) {
            return;
        }
        int duration = movieRepository.findById(movieId)
                .map(Movie::getDurationMinutes)
                .orElseThrow(() -> new IllegalStateException("Película no encontrada: " + movieId));

        sessionRepository.save(Session.builder()
                .id(id)
                .movieId(movieId)
                .roomId(roomId)
                .startTime(start)
                .endTime(start.plusMinutes(duration + PREPARATION_MINUTES))
                .status("A_LA_VENTA")
                .ticketPrice(price)
                .build());
    }
}
```

Y en `componente1-cartelera/src/main/resources/application.yaml`:

```yaml
app:
  seed:
    enabled: ${APP_SEED_ENABLED:true}
```

---

## 7. Checklist de verificación

- [ ] `docker compose up -d --build` arranca sin errores.
- [ ] `GET /api/v1/cinemas` devuelve 2 cines con ids `"1"` y `"2"`.
- [ ] `GET /api/v1/cinemas/1/rooms` devuelve 3 salas; `/cinemas/2/rooms`, 2.
- [ ] `GET /api/v1/movies` devuelve 8 películas con ids `"1001"`–`"1008"`.
- [ ] `GET /api/v1/rooms` (con token) devuelve 5 salas con capacidades 120, 80, 48, 120 y 80.
- [ ] `GET /api/v1/sessions` devuelve 18 sesiones.
- [ ] `GET /api/v1/movies?inShowcase=true` devuelve las 8 (todas tienen sesión futura).
- [ ] Todas las sesiones están en `A_LA_VENTA`.
- [ ] Dos sesiones de la misma sala no se solapan.
- [ ] Reiniciar el servicio no duplica documentos (idempotencia).
- [ ] `POST /v1/reservations` con `showId: "3001"` funciona (el C2 acepta la sesión).
- [ ] Tras pagar, el evento llega a RabbitMQ y el C4 devuelve ocupación > 0.

---

## 8. Comandos útiles

```bash
# Login como admin
TOKEN=$(curl -s -X POST http://localhost:8083/v1/iam/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@cinasee.com","password":"Admin123!"}' \
  | jq -r .token)

# Catálogo
curl http://localhost:8081/api/v1/cinemas | jq
curl http://localhost:8081/api/v1/cinemas/1/rooms | jq
curl http://localhost:8081/api/v1/movies | jq
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/v1/rooms | jq
curl http://localhost:8081/api/v1/sessions | jq

# Comprobar el estado de una sesión
curl http://localhost:8081/api/v1/sessions/3001 | jq .status

# Poner una sesión a la venta (si se creó por API y nació PROGRAMADA)
curl -X PATCH http://localhost:8081/api/v1/sessions/3001 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status":"A_LA_VENTA"}'

# Inspección directa de Mongo
docker exec -it mongodb-cartelera mongosh cine_catalog_db --eval \
  'db.cinemas.countDocuments(); db.movies.countDocuments(); db.rooms.countDocuments(); db.sessions.countDocuments()'

```
