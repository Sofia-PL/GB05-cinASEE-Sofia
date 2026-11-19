# Ejecución y uso del Componente 1

## 1. Requisitos

- JDK 21.
- Docker y Docker Compose para usar MongoDB en contenedor.
- Componente 3 levantado para obtener JWT válidos para las rutas protegidas.

## 2. Ejecutar las pruebas

Desde la raíz del repositorio:

```bash
cd componente1-cartelera
bash ./mvnw test
```

En Windows:

```powershell
cd componente1-cartelera
.\mvnw.cmd test
```

La suite incluye pruebas unitarias de servicios, pruebas HTTP del controlador y
una prueba de aceptación que recorre la creación de película, sala y sesión.
Mockito sustituye los repositorios o servicios en las pruebas que lo requieren;
no hace falta una instancia real de MongoDB para ejecutar los tests.

## 3. Levantar MongoDB

Los servicios del Componente 1 están definidos en el `docker-compose.yml` de la
raíz. Desde dicha raíz, arranca MongoDB:

```bash
docker compose up -d mongodb-cartelera
docker compose ps mongodb-cartelera
```

El contenedor publica MongoDB solo en `127.0.0.1:27017` y conserva los datos en
el volumen `mongo_data`. Espera a que aparezca como `healthy` antes de iniciar la
aplicación.

Comprueba que responde:

```bash
docker compose exec mongodb-cartelera mongosh --quiet --eval 'db.adminCommand({ ping: 1 })'
```

Una conexión correcta devuelve un documento con `ok: 1`. Para ver la base y las
colecciones existentes:

```bash
docker compose exec mongodb-cartelera mongosh cine_catalog_db --quiet --eval 'db.getCollectionNames()'
```

Se esperan las colecciones `movies`, `rooms` y `sessions` después de que se hayan
guardado documentos. En una base nueva, la lista puede estar vacía hasta la
primera escritura.

## 4. Iniciar el Componente 1

### Con Docker Compose

Desde la raíz del repositorio:

```bash
docker compose up -d --build componente1-cartelera
docker compose ps componente1-cartelera mongodb-cartelera
docker compose logs --tail=100 componente1-cartelera
```

Compose inicia también los servicios requeridos, incluido MongoDB y el
Componente 3. La aplicación queda publicada en `http://localhost:8081`. Si el
contenedor aparece como iniciado, prueba el endpoint público:

```bash
curl -i http://localhost:8081/api/v1/movies
```

Al arrancar, `CatalogSeeder` carga automáticamente el catálogo de demostración
si `APP_SEED_ENABLED=true`. Esta carga es idempotente. Puede desactivarse con:

```bash
APP_SEED_ENABLED=false docker compose up -d --build componente1-cartelera
```

Los pósteres se pueden consultar en
`http://localhost:8080/media/posters/<archivo>.jpg` cuando está levantado el
Gateway y `media-server`.

### Con Maven

Arranca primero MongoDB según la sección anterior. Desde `componente1-cartelera`:

```bash
bash ./mvnw spring-boot:run
```

La configuración local usa `mongodb://localhost:27017/cine_catalog_db`. El
servidor escucha en el puerto `8081` y expone la API bajo `/api/v1`. Para validar
JWT, el Componente 3 debe estar disponible en la URL configurada en
`src/main/resources/application.yaml`.

La URI se configura con `spring.mongodb.uri` (variable de entorno
`SPRING_MONGODB_URI`). No uses `SPRING_DATA_MONGODB_URI`: esa propiedad antigua
dejó de ser válida en Spring Boot 4 y la aplicación recurriría al Mongo local
predeterminado dentro del contenedor.

## 5. Usar la API

### Consultar películas y cartelera

Estas consultas son públicas; se pueden usar sin JWT:

```bash
curl -i http://localhost:8081/api/v1/movies
curl -i http://localhost:8081/api/v1/sessions
curl -i 'http://localhost:8081/api/v1/movies?genre=DRAMA_ROMANTICO'
curl -i 'http://localhost:8081/api/v1/sessions?date=2030-01-18&movieId=101'
```

Con el servicio disponible, las consultas deben responder `200 OK`, aunque
todavía no haya documentos y el cuerpo sea `[]`. Los filtros de sesiones `date`
y `movieId` se pueden combinar.

### Obtener un JWT

Las operaciones de escritura y las consultas de salas y sesiones individuales
requieren un JWT válido emitido por el Componente 3. Para solicitar un token de
invitado en desarrollo:

```bash
curl -s -X POST http://localhost:8083/v1/iam/auth/guest \
  -H 'Content-Type: application/json' \
  -d '{"deviceId":"componente1-docs"}'
```

La respuesta incluye `accessToken`. Copia el valor de autorización en la
variable `TOKEN`: el valor completo debe incluir el esquema `Bearer` seguido
del token emitido. Para introducirlo sin guardarlo en el historial:

```bash
read -r -s -p 'Valor del header Authorization: ' TOKEN
echo
```

También se puede usar un JWT obtenido mediante el login de un usuario registrado
en `POST http://localhost:8083/v1/iam/auth/login`. Sin token, una ruta protegida
responde `401 Unauthorized`. Si el Componente 3 se reinicia y genera una nueva
clave de firma, solicita un JWT nuevo.

### Crear cine, película, sala y sesión

Las respuestas de creación incluyen los identificadores generados. Utiliza esos
IDs en los siguientes pasos:

```bash
# Crear un cine (obligatorio antes de crear salas)
curl -i -X POST http://localhost:8081/api/v1/cinemas \
  -H "Authorization: $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Cine de ejemplo",
    "address": "Calle de ejemplo 1",
    "city": "Madrid",
    "phone": "910000000"
  }'

# Crear una película
curl -i -X POST http://localhost:8081/api/v1/movies \
  -H "Authorization: $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "title": "Película de ejemplo",
    "synopsis": "Descripción de ejemplo",
    "durationMinutes": 100,
    "genre": "DRAMA",
    "ageRating": "TP"
  }'

# Crear una sala dentro del cine; el aforo se calcula como filas por columnas
curl -i -X POST http://localhost:8081/api/v1/rooms \
  -H "Authorization: $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "cinemaId": 1,
    "name": "Sala de ejemplo",
    "roomType": "ESTANDAR_2D",
    "rowsCount": 8,
    "columnsCount": 12,
    "wheelchairAccessible": true,
    "basePrice": 8.50
  }'

# Programar una sesión; sustituye movieId y roomId por los IDs recibidos arriba
curl -i -X POST http://localhost:8081/api/v1/sessions \
  -H "Authorization: $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "movieId": 101,
    "roomId": 1,
    "startTime": "2030-01-18T18:30:00",
    "ticketPrice": 8.50
  }'
```

`cinemaId` debe existir: si no, `POST /rooms` responde `404`. La película ya no
tiene campo `distributor`; si se envía, se ignora.

`startTime` se envía en formato ISO de fecha y hora (`yyyy-MM-dd'T'HH:mm:ss`),
sin sufijo de zona horaria, porque el modelo del servicio usa `LocalDateTime`.
La sesión se crea con estado `PROGRAMADA`; la hora de fin se calcula sumando la
duración de la película y 20 minutos de preparación. Si el horario se solapa con
otra sesión no cancelada en la misma sala, se recibe `409 Conflict`.

### Consultar y actualizar sesiones

```bash
# Consultar una sala (requiere JWT)
curl -i http://localhost:8081/api/v1/rooms/1 \
  -H "Authorization: $TOKEN"

# Consultar una sesión por ID (requiere JWT)
curl -i http://localhost:8081/api/v1/sessions/5001 \
  -H "Authorization: $TOKEN"

# Cambiar el estado para poner la sesión a la venta
curl -i -X PATCH http://localhost:8081/api/v1/sessions/5001 \
  -H "Authorization: $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"status":"A_LA_VENTA"}'

# Cancelar una sesión (se conserva el registro)
curl -i -X PATCH http://localhost:8081/api/v1/sessions/5001 \
  -H "Authorization: $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"status":"CANCELADA"}'
```

Para mover una sesión se pueden enviar `roomId` y/o `startTime` en el mismo
`PATCH`; el servicio recalcula el fin y vuelve a comprobar los solapamientos.
Los estados admitidos por el contrato son `PROGRAMADA`, `A_LA_VENTA`, `EN_CURSO`,
`FINALIZADA` y `CANCELADA`.

Para abrir el contrato de endpoints y esquemas, consulta
[componente_1.yaml](../openapi/componente_1.yaml).

## 6. Parar servicios

Desde la raíz del repositorio:

```bash
docker compose stop componente1-cartelera mongodb-cartelera
```

Los datos persisten en `mongo_data`. No elimines el volumen salvo que quieras
borrar intencionadamente los datos del catálogo.
