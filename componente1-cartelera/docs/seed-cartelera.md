# Estado de datos y carga del catálogo — Componente 1

## Estado actual

C1 carga el catálogo automáticamente al arrancar mediante `CatalogSeeder`.
La carga puede desactivarse con `APP_SEED_ENABLED=false`. También existe el
script manual
[`scripts/seed-catalog.mongodb.js`](../scripts/seed-catalog.mongodb.js).
Ambos mecanismos producen 7 cines, 8 películas, 12 salas y 278 sesiones para los
14 días siguientes al primer arranque: 48 en los 3 primeros días (la programación
base) y, del día 4 al 14, los horarios del día 1 y del día 2 alternos. Así hay
cartelera aunque la aplicación se pruebe días después de levantarla. Las fechas se
fijan en el primer arranque (o la primera ejecución del script) y no se mueven después.
Una sesión de ejemplo que choque con otra ya existente en la misma sala no se crea.
Son idempotentes: no duplican ni modifican documentos existentes con los mismos
IDs.

Los pósteres incluidos están empaquetados en `media/posters/` y se publican
mediante `media-server` a través del Gateway en
`http://localhost:8080/media/posters/<nombre>.jpg`.

## Persistencia y limpieza

- Base de datos: `cine_catalog_db`.
- URI local predeterminada: `mongodb://localhost:27017/cine_catalog_db`.
- Docker Compose usa el contenedor `mongodb-cartelera` y el volumen
  persistente `mongo_data`.
- Colecciones: `cinemas`, `movies`, `rooms` y `sessions`.
- Reiniciar los servicios no vacía la base ni su volumen.

Para borrar todos los documentos, colecciones e índices de C1, con MongoDB
activo, detén C1 y elimina únicamente su base:

```bash
docker compose stop componente1-cartelera
docker exec mongodb-cartelera mongosh cine_catalog_db --quiet --eval 'db.dropDatabase()'
```

La respuesta debe indicar `dropped: 'cine_catalog_db'`. Para comprobar que la
base ya no aparece:

```bash
docker exec mongodb-cartelera mongosh admin --quiet --eval \
  'printjson(db.adminCommand({listDatabases: 1, nameOnly: true}).databases.filter(d => d.name === "cine_catalog_db"))'
```

El resultado esperado es `[]`. Esto no elimina el volumen Docker ni afecta a
las bases de datos de C2–C5. Si se usa una instancia local en vez de Docker:

```bash
mongosh "mongodb://localhost:27017/cine_catalog_db" --quiet --eval 'db.dropDatabase()'
```

## Modelo e identificadores

Todos los IDs y referencias se almacenan como texto. La API genera UUID para
cines, películas y salas, y ULID monotónicos para sesiones. El script usa IDs
UUID/ULID fijos y válidos para que sus referencias sean reproducibles.

| Colección | Datos principales | ID generado al crear |
|---|---|---|
| `cinemas` | Nombre, dirección, ciudad y teléfono | UUID (`UUID.randomUUID().toString()`) |
| `movies` | Título, sinopsis, duración, género, clasificación, póster y tráiler | UUID (`UUID.randomUUID().toString()`) |
| `rooms` | Cine, nombre, tipo, dimensiones, capacidad, accesibilidad y precio base | UUID (`UUID.randomUUID().toString()`) |
| `sessions` | Película, sala, inicio, fin, estado y precio de entrada | ULID monotónico de `ulid-creator` |

Los IDs de las altas hechas por la API son nuevos; conserva los que devuelve
para establecer las referencias entre recursos. Para el cine de Madrid creado
por el script, el ID fijo es
`550e8400-e29b-41d4-a716-446655440001`. El `docker-compose.yml` raíz ya
configura `APP_SEED_STAFF_CINEMA_ID` con ese ID, de modo que el usuario demo de
taquilla de C3 queda asignado al mismo cine; el seed de C1 no modifica usuarios
ni configuración de otros componentes.

## Ejecutar el seed

Desde la raíz del repositorio, con Docker y `mongodb-cartelera` activos:

```bash
docker cp componente1-cartelera/scripts/seed-catalog.mongodb.js mongodb-cartelera:/tmp/seed-catalog.mongodb.js
docker exec mongodb-cartelera mongosh cine_catalog_db --quiet --file /tmp/seed-catalog.mongodb.js
docker exec mongodb-cartelera rm /tmp/seed-catalog.mongodb.js
```

El script verifica que la base seleccionada se llame exactamente
`cine_catalog_db`; si se ejecuta contra otra base, termina con error. Inserta
los recursos en orden para mantener las referencias y conserva los documentos
existentes. Las sesiones quedan en `A_LA_VENTA`, con horas locales de
`Europe/Madrid` relativas al día de ejecución, y sus precios no son inferiores
al precio base de sus salas.
El calendario reparte las sesiones entre los siete cines: Madrid 18,
Barcelona 8, Cáceres 6, Navatrasierra 3, Madroñera 3, Málaga 7 y Cuenca 3.

Para comprobar los datos:

```bash
docker exec mongodb-cartelera mongosh cine_catalog_db --quiet --eval \
  'printjson(db.getCollectionNames().map(name => ({collection: name, documents: db.getCollection(name).countDocuments()})))'
```

El resultado del seed es `cinemas: 7`, `movies: 8`, `rooms: 12` y
`sessions: 48`.

## Carga de datos mediante la API

Ruta base: `http://localhost:8081/api/v1`. Para crear un catálogo relacionado,
usa este orden:

1. `POST /cinemas` y `POST /movies`.
2. `POST /rooms`, usando el ID real de un cine existente en `cinemaId`.
3. `POST /sessions`, usando los IDs reales de una película y una sala.
4. Cambia la sesión a `A_LA_VENTA` con `PATCH /sessions/{sessionId}` antes de
   aceptar reservas desde C2.

Los `POST` y el `PATCH` requieren un JWT válido. Las sesiones nuevas nacen en
`PROGRAMADA`; su `endTime` se calcula con la duración de la película más 20
minutos de preparación. C1 rechaza con `409 Conflict` los horarios que se
solapan en la misma sala. La capacidad de una sala se calcula como
`rowsCount * columnsCount`.

`Session.startTime` es un `LocalDateTime`, sin zona horaria. Coordina
`CATALOG_TIME_ZONE` de C2 con la zona horaria usada al enviar las horas.

## Consultas de comprobación

Las lecturas de cines, películas y listado de sesiones son públicas. Las
consultas de salas requieren un JWT.

```bash
curl http://localhost:8081/api/v1/cinemas
curl http://localhost:8081/api/v1/movies
curl http://localhost:8081/api/v1/sessions
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/v1/rooms
```

## Tests

Los tests sustituyen los repositorios con mocks y no requieren una instancia
de MongoDB:

```bash
cd componente1-cartelera
./mvnw test
```
