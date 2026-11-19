# Cómo probar el Componente 2 de forma aislada

Para probar Reservas no hace falta levantar Cartelera ni Usuarios:

| Lo que falta | Cómo se sustituye |
|---|---|
| Comp. 1 Cartelera (`:8081`) | **WireMock** con respuestas fijas (`wiremock/mappings/cartelera.json`) |
| Comp. 3 Usuarios (`:8083`) | WireMock (`wiremock/mappings/usuarios.json`) |
| Tokens del Comp. 3 (RS256) | Tokens HS256 de desarrollo creados con `scripts/generate-dev-token.ps1` |
| Pasarela de pago | Pasarela simulada (o la web de pruebas real de Redsys con `PAYMENT_MODE=redsys`) |
| Comp. 3 y 4 consumiendo eventos | Cola de depuración `reservas.debug.payment-events` |

Todo esto lo activa el perfil de Spring **`isolated`** (`application-isolated.yaml`).
**No debe usarse nunca en producción.**

---

## 0. Requisitos

- **JDK 21**. Comprueba que `JAVA_HOME` apunta a él, porque si apunta al 17 Maven
  falla con `release version 21 not supported`:
  ```powershell
  $env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
  ```
- **Docker Desktop arrancado**, para PostgreSQL, RabbitMQ y WireMock.

Opcionalmente, la extensión de VS Code **REST Client** (`humao.rest-client`) para
lanzar `http/reservas.http`.

> **Puerto de PostgreSQL:** el contenedor se publica en el **5433** y no en el 5432,
> porque en el equipo de desarrollo ya hay un PostgreSQL instalado escuchando en el
> 5432. El perfil `isolated` usa el 5433 por defecto.
>
> **No lo mezcles con el `docker-compose.yml` de la raíz.** Ese también publica
> PostgreSQL en el 5433 y RabbitMQ en el 5672. Antes de usar este entorno aislado,
> para el global con `docker compose down` desde la raíz, y al revés.

---

## 1. Solo los tests (sin Docker)

```powershell
cd componente2-reservas
.\mvnw.cmd test
```

Usan H2 en memoria y *mocks*, así que no necesitan nada más.

---

## 2. Levantar la infraestructura

```powershell
cd componente2-reservas
docker compose up -d
docker compose ps        # postgres-reservas y rabbitmq deben salir "healthy"
```

| Servicio | Equipo | Notas |
|---|---|---|
| PostgreSQL | `localhost:5433` | BD `cine_reservas_db`, usuario y contraseña `postgres` |
| RabbitMQ | `localhost:5672`, consola <http://localhost:15672> | `guest` / `guest` |
| WireMock | <http://localhost:8089> | Simula Cartelera y Usuarios. Lista de stubs: <http://localhost:8089/__admin/mappings> |

> Si ya tienes el RabbitMQ del Componente 3 (`cinasee-rabbitmq`) ocupando el 5672,
> levanta solo lo demás: `docker compose up -d postgres-reservas wiremock`. Reservas
> usará ese RabbitMQ, y así el Componente 3 recibirá de verdad los eventos.

---

## 3. Arrancar el microservicio

**Opción A, con Maven (recomendada para desarrollar):**

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=isolated"
```

**Opción B, todo en Docker:**

```powershell
docker compose --profile app up -d --build
docker compose logs -f reservas
```

Está listo cuando aparece `Tomcat started on port 8082 (http) with context path '/v1'`.

Datos que simula WireMock:

| Recurso | Valor | Para probar |
|---|---|---|
| Sesión `1024` | A_LA_VENTA, viernes 18/01/2030, 8,50 € | Reserva normal |
| Sesión `1025` | A_LA_VENTA, **miércoles** 16/01/2030 | Día del espectador (−20 %) |
| Sesión `2048` | PROGRAMADA | 409 `SHOW_NOT_ON_SALE` |
| Sesión `5030` | Cartelera responde 500 | 503 |
| Cualquier otra sesión | 404 | 404 |
| Sala `1` | 10 filas × 15 columnas | Butaca `11-1` → 400 |
| Usuario `5590` | 1250 puntos | Fidelización (−10 %) |
| Usuario `42` | 100 puntos | Sin descuento |

---

## 4. Generar tokens

```powershell
.\scripts\generate-dev-token.ps1 -Subject 5590 -Role CLIENTE_REGISTRADO
.\scripts\generate-dev-token.ps1 -Subject "guest:demo-1" -Role VISITANTE
.\scripts\generate-dev-token.ps1 -Subject 1 -Role STAFF_TAQUILLA
```

Cada token dura 2 horas (`-Minutes` para cambiarlo) y lleva los mismos claims que
los del Componente 3: `iss`, `sub`, `role`, `iat`, `exp`, `jti`.

> Si PowerShell bloquea el script por la política de ejecución:
> `powershell -ExecutionPolicy Bypass -File .\scripts\generate-dev-token.ps1 ...`

---

## 5. Lanzar peticiones

### Con REST Client (VS Code)

Abre `http/reservas.http`, pega los tres tokens en las variables del principio y
pulsa **Send Request** en cada bloque. Hay 20 escenarios, de éxito y de error, en
orden. Las peticiones encadenadas reutilizan el `id` de la reserva y el código QR.

### Con PowerShell

```powershell
$base   = "http://localhost:8082/v1"
$token  = .\scripts\generate-dev-token.ps1 -Subject 5590 -Role CLIENTE_REGISTRADO
$auth   = @{ Authorization = "Bearer $token" }

# Reservar dos butacas
$r = Invoke-RestMethod -Method Post "$base/reservations" -Headers $auth -ContentType "application/json" `
     -Body '{"showId":"1024","seats":[{"row":8,"column":12},{"row":8,"column":13}]}'
$r   # status PENDING_PAYMENT, totalAmount 15.30 (8,50 € − 10 % x 2), expiresAt = ahora + 10 min

# Pagar
Invoke-RestMethod -Method Post "$base/reservations/$($r.id)/payment" -Headers $auth `
     -ContentType "application/json" -Body '{"gateway":"REDSYS","paymentToken":"tok_visa_4242"}'
# status CONFIRMED y tickets: ["QR-1-8-12-XXXXXXXX", "QR-1-8-13-XXXXXXXX"]

# Mapa de butacas (sin token)
Invoke-RestMethod "$base/reservations/shows/1024/seats"
```

---

## 6. Comprobar cada pieza

**El evento en RabbitMQ.** En <http://localhost:15672>, ve a *Queues* →
`reservas.debug.payment-events` → *Get messages*. Tras cada pago aparece un
`PaymentCompleted` como el de [evento-payment-completed.md](evento-payment-completed.md).
Esa cola solo existe en el perfil `isolated`.

**El outbox.**
```powershell
docker exec -it cinasee-postgres-reservas psql -U postgres -d cine_reservas_db `
  -c "SELECT event_type, published_at, attempts, last_error FROM outbox_events;"
```
Para ver la resiliencia: `docker compose stop rabbitmq`, paga una reserva, y verás
el evento con `published_at` vacío y `attempts` subiendo. Con
`docker compose start rabbitmq` se publica solo en unos segundos.

**El TTL sin esperar 10 minutos.** Arranca con un TTL corto:
```powershell
$env:RESERVATION_HOLD_TTL="30s"; $env:RESERVATION_SWEEP_INTERVAL="5s"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=isolated"
```
Reserva y espera 30 s. El pago devuelve 400 `RESERVATION_EXPIRED` y la butaca
vuelve a estar libre en el mapa.

**La doble venta.** Reserva la butaca `8-12` con un token y repítelo con otro
token: el segundo recibe 409 con `"details": ["8-12"]`. El caso realmente
simultáneo (8 hilos a la vez) lo cubre `ReservationFlowIntegrationTest`.

**Las dependencias caídas.** `docker compose stop wiremock` y crea una reserva:
debe responder 503 `SERVICE_UNAVAILABLE`.

**El pago real con Redsys (web de pruebas).** Necesita internet. Arranca Reservas
con Redsys activo:
```powershell
$env:PAYMENT_MODE="redsys"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=isolated"
```
(En el `docker-compose.yml` global ya viene activado.) Luego crea una reserva como en
el apartado 5 y abre la web de pago:
```powershell
.\scripts\pagar-redsys.ps1 -ReservationId $r.id -Token $cliente
```
El navegador va a la web de pruebas de Redsys. Datos de la tarjeta de prueba (manual
oficial de Redsys, *Datos genéricos de prueba*):

| Campo | Valor |
|---|---|
| Número | `4548 8120 4940 0004` |
| Caducidad | `12/20` según el manual (si Redsys la rechaza por caducada, prueba con una fecha futura, por ejemplo `12/49`) |
| CVV | `123` |
| Código de autenticación (3D Secure / CIP) | `123456` |

Al terminar, Redsys devuelve el navegador a `http://localhost:8082/v1/reservations/payments/redsys/return`,
que muestra **Pago confirmado** con las entradas. Si cancelas en Redsys o la tarjeta
se deniega, muestra el motivo y la reserva sigue pendiente para reintentar.

> **Con el frontend.** En el `docker-compose.yml` global, `REDSYS_URL_OK` y `REDSYS_URL_KO`
> apuntan a `http://localhost:3000/pago/redsys/vuelta`: Redsys devuelve el navegador al
> frontend, que reenvía la respuesta a `POST /v1/reservations/{id}/payment/redsys/confirm`
> a través del gateway. En modo aislado se sigue usando la página propia de Reservas.

Los datos del comercio (número `999008881`, terminal 1 y clave) son los **genéricos de
prueba publicados por Redsys** y ya están en `application.yaml`: no hay que crear ni
configurar nada.

---

## 7. Parar

```powershell
docker compose --profile app down      # conserva los datos
docker compose --profile app down -v   # borra también la base de datos
```

---

## 8. Probar contra los componentes reales

Sin el perfil `isolated`, Reservas usa los servicios reales:

```powershell
# Comp. 3 en :8083 y Comp. 1 en :8081 ya arrancados
$env:POSTGRES_PORT="5433"   # si usas el PostgreSQL del docker-compose de esta carpeta
.\mvnw.cmd spring-boot:run
```

El token se obtiene del Componente 3 (`POST http://localhost:8083/v1/iam/auth/login`
o `/auth/guest`). Se valida con su JWKS y se reenvía a Cartelera y Usuarios.
Ten en cuenta que el Comp. 3 genera una clave RSA nueva en cada arranque: si lo
reinicias, pide un token nuevo.
