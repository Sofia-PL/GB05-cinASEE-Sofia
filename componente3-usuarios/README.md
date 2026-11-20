# Componente 3 — IAM y Fidelización

Microservicio de **gestión de identidades (IAM)** y **fidelización** de CinAsee.
Es el único componente que emite tokens: el resto de microservicios solo los
validan con la clave pública que este servicio publica.

- **Tecnología:** Java 21 + Spring Boot 4
- **Puerto:** `8083`
- **Ruta base:** `/v1/iam`
- **Base de datos:** MySQL (`cinasee_users_db`)
- **Caché:** Redis (rate limit de invitados)
- **Mensajería:** RabbitMQ (consumidor de `payment.completed`)

---

## 1. Qué hace

| Área | Responsabilidad |
|---|---|
| Autenticación | Login con email/contraseña y emisión de JWT firmados |
| Registro | Alta de clientes con rol `CLIENTE_REGISTRADO` |
| Invitados | Tokens de corta duración para comprar sin registrarse |
| Perfiles | Consulta y edición de datos personales y géneros preferidos |
| Historial | Compras asociadas al usuario |
| Roles | Solicitud y revisión de roles internos (`STAFF_TAQUILLA`, `ADMIN`) |
| Fidelización | Puntos por compra y descuentos por umbral |
| Metadatos | JWKS y configuración OIDC para que otros servicios validen tokens |

---

## 2. Arquitectura interna

```
com.cinasee.componente3_usuarios
├── config/       SecurityConfig (RSA, JWT, filtros), RabbitMQ, seed de datos
├── controllers/  Adaptadores REST (implementan las interfaces generadas del OpenAPI)
├── dto/          Modelos generados a partir de openapi/componente_3.yaml
├── events/       Contrato y listener de PaymentCompleted
├── models/       Entidades JPA (User, PurchaseHistory, RoleRequest)
├── repositories/ Repositorios Spring Data JPA
├── security/     JwtProvider y JwtAuthenticationFilter
└── services/     Lógica de negocio (auth, usuarios, roles, fidelización, rate limit)
```

El contrato HTTP vive en `openapi/componente_3.yaml` y se compila con
`openapi-generator-maven-plugin` (`interfaceOnly`). Los controladores solo
implementan esas interfaces; la lógica está en `services/`.

---

## 3. Puesta en marcha

### Con Docker (todo el sistema)

Desde la raíz del repositorio:

```bash
# Windows
.\ARRANCAR.bat
# macOS / Linux
./scripts/iniciar.sh
```

El script construye las imágenes, levanta todos los servicios, espera a que
respondan por HTTP e imprime un resumen. También puede hacerse a mano:

```bash
docker compose up -d --build
```

Arranca MySQL, Redis, RabbitMQ y el microservicio. El seed de usuarios se
ejecuta automáticamente en el primer arranque.

### En local

Necesita MySQL, Redis y RabbitMQ accesibles (por ejemplo, los contenedores del
compose):

```bash
docker compose up -d mysql-usuarios redis-usuarios rabbitmq
cd componente3-usuarios
./mvnw spring-boot:run
```

### Tests

```bash
cd componente3-usuarios
./mvnw test
```

---

## 4. Endpoints

Todas las rutas cuelgan de `http://localhost:8083/v1/iam`.

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| `POST` | `/auth/login` | Público | Devuelve `token`, `tokenType`, `expiresIn` y `role` |
| `POST` | `/auth/register` | Público | Crea un cliente registrado (201) |
| `POST` | `/auth/guest` | Público + rate limit | Token de invitado (`VISITANTE`) |
| `GET` | `/users/{userId}` | El propio usuario, `STAFF_TAQUILLA` o `ADMIN` | Perfil |
| `PUT` | `/users/{userId}` | El propio usuario o `ADMIN` | Edita perfil |
| `GET` | `/users/{userId}/history` | El propio usuario, `STAFF_TAQUILLA` o `ADMIN` | Historial de compras |
| `POST` | `/role-requests` | `CLIENTE_REGISTRADO`, `STAFF_TAQUILLA` | Solicita un rol |
| `GET` | `/role-requests/me` | Usuario autenticado | Mis solicitudes |
| `GET` | `/role-requests` | `STAFF_TAQUILLA`, `ADMIN` | Lista solicitudes (filtros `status`, `cinemaId`) |
| `PATCH` | `/role-requests/{requestId}` | `STAFF_TAQUILLA`, `ADMIN` | Aprueba o rechaza |
| `GET` | `/.well-known/jwks.json` | Público | Clave pública RSA (JWKS) |
| `GET` | `/.well-known/openid-configuration` | Público | Metadatos OIDC |

Ejemplo de login:

```bash
curl -X POST http://localhost:8083/v1/iam/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@cinasee.com","password":"Admin123!"}'
```

```json
{
  "token": "eyJraWQiOi...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "role": "ADMIN"
}
```

---

## 5. Seguridad: por qué RS256 y no un JWT simétrico

Un JWT se puede firmar de dos formas:

- **HS256 (simétrico):** una única clave secreta firma y verifica.
- **RS256 (asimétrico):** una clave **privada** firma y una clave **pública** verifica.

En una arquitectura de microservicios, HS256 obliga a que **todos** los
servicios compartan el mismo secreto. Eso implica:

1. Cualquier servicio comprometido puede **fabricar tokens válidos** para
   cualquier usuario y rol.
2. Cada rotación de la clave exige **redistribuir el secreto** a todos los
   servicios y reiniciarlos de forma coordinada.
3. El secreto acaba duplicado en N configuraciones, N imágenes y N entornos.

Con RS256:

1. **Solo el Componente 3 posee la clave privada.** Puede firmar; nadie más.
2. Los demás servicios descargan la **clave pública** de
   `/.well-known/jwks.json` y verifican la firma. Una clave pública no permite
   firmar, así que un servicio comprometido no puede suplantar identidades.
3. La rotación es limpia: se publica una clave nueva en el JWKS y los servicios
   la recogen sin compartir secretos.
4. La validación es **local**: no se llama al Componente 3 en cada petición,
   solo se descarga (y se cachea) el JWKS.
5. Es el mecanismo estándar de OIDC/OAuth2, así que encaja con Spring Security
   como *Resource Server* sin código propio.

```
Componente 3 ──firma con clave privada──▶ JWT
      │
      └──publica clave pública──▶ /.well-known/jwks.json
                                        │
        C1, C2, C4 y C5 ──descargan─────┘
                 y verifican la firma en local
```

### Implementación en este proyecto

`SecurityConfig` genera un par RSA de 2048 bits **al arrancar** y lo guarda en
memoria. Así nunca se commitea una clave privada al repositorio.

> **Consecuencia:** al reiniciar el Componente 3 se genera una clave nueva y los
> tokens emitidos antes dejan de ser válidos. En producción la clave debería
> persistirse en un KMS/HSM o en un keystore seguro y rotarse periódicamente.

El `JwtDecoder` local valida además el emisor (`iss`), de modo que un token
firmado por otro emisor se rechaza.

### Claims del token

| Claim | Contenido |
|---|---|
| `iss` | `http://localhost:8083/v1/iam` |
| `sub` | `id` del usuario (o `guestId` en invitados) |
| `iat` / `exp` | Emisión y caducidad |
| `jti` | UUID único del token |
| `email` | Email del usuario |
| `role` | `VISITANTE`, `CLIENTE_REGISTRADO`, `STAFF_TAQUILLA` o `ADMIN` |
| `guest` | `true` solo en tokens de invitado |
| `device_id` | Identificador del dispositivo (invitados) |

Caducidad: **3600 s** para usuarios y **900 s** para invitados
(`JWT_EXPIRATION_SECONDS`, `JWT_GUEST_EXPIRATION_SECONDS`).

Los servicios convierten el claim `role` en autoridades `ROLE_<rol>` mediante un
`JwtAuthenticationConverter`.

---

## 6. Roles

| Rol | Uso |
|---|---|
| `VISITANTE` | Compra como invitado; sin acceso a datos de usuario |
| `CLIENTE_REGISTRADO` | Cliente con perfil, historial y puntos |
| `STAFF_TAQUILLA` | Personal de cine; puede ser `cinemaManager` de un cine |
| `ADMIN` | Administración global |

Roles solicitables (`POST /role-requests`): `STAFF_TAQUILLA` y `ADMIN`.
Un `ADMIN` no puede solicitar otro rol: el `@PreAuthorize` del controlador le
devuelve **403** (el servicio mantiene además un 409 defensivo si se invocara
directamente).

Un `STAFF_TAQUILLA` con `cinemaManager = true` solo puede revisar solicitudes de
**su** cine (`cinemaId`). Ese flag **solo lo asigna el seed**: aprobar una
solicitud `STAFF_TAQUILLA` cambia el rol y el cine, pero no convierte al nuevo
taquillero en `cinemaManager`.

### Quién puede cambiar qué

Las lecturas y las escrituras no tienen el mismo permiso. Esta es la política
real que aplica `SecurityConfig` junto con las anotaciones `@PreAuthorize` y las
comprobaciones de `RoleRequestServiceImpl`:

| Acción | Quién puede | Efecto |
|---|---|---|
| Ver perfil e historial (`GET /users/{id}`, `/history`) | El propio usuario, `STAFF_TAQUILLA` o `ADMIN` | Solo lectura |
| Editar perfil (`PUT /users/{id}`) | El propio usuario o `ADMIN` | `STAFF_TAQUILLA` **no** puede editar datos personales |
| Solicitar un rol (`POST /role-requests`) | `CLIENTE_REGISTRADO` o `STAFF_TAQUILLA` | Un `ADMIN` recibe **403** |
| Ver solicitudes (`GET /role-requests`) | `ADMIN` (todas) o `STAFF_TAQUILLA` con `cinemaManager` (solo las de su cine) | — |
| Aprobar o rechazar (`PATCH /role-requests/{id}`) | `ADMIN` cualquiera; `STAFF_TAQUILLA` solo solicitudes `STAFF_TAQUILLA` de su cine y nunca la propia | Al aprobar cambia `role` y `cinemaId` del solicitante |
| Modificar puntos de fidelización | **Nadie por API** | Solo el evento `PaymentCompleted` suma puntos |

En resumen: **los cambios sensibles (rol, cine asignado y datos de otros
usuarios) son exclusivos de `ADMIN`**; cada usuario solo edita su propio perfil y
`STAFF_TAQUILLA` únicamente consulta.

> Un `STAFF_TAQUILLA` puede consultar el perfil y el historial de **cualquier**
> usuario, no solo de los de su cine: la restricción por `cinemaId` se aplica a
> las solicitudes de rol, no a los datos de clientes.

---

## 7. Modelo de datos

### `User`

| Campo | Notas |
|---|---|
| `id` | Autoincremental |
| `email` | Único, normalizado a minúsculas |
| `passwordHash` | BCrypt |
| `fullName` | Nombre visible |
| `role` | Enum, por defecto `CLIENTE_REGISTRADO` |
| `loyaltyPoints` | Puntos acumulados (no negativos) |
| `phone` | Teléfono de contacto |
| `homeCinema` | Cine habitual (texto) |
| `cinemaId` | Cine asignado a roles internos |
| `cinemaManager` | Taquillero responsable de su cine |
| `preferredGenres` | Tabla `user_preferred_genres` |
| `createdAt` | Fecha de alta |

### `PurchaseHistory`
Compras asociadas al usuario (película, importe, fecha), alimentadas por el
evento `PaymentCompleted`.

### `RoleRequest`
Solicitud de rol con estado (`PENDING`, `APPROVED`, `REJECTED`), rol pedido y
cine de referencia.

---

## 8. Seed de usuarios

`DemoDataInitializer` crea tres usuarios al arrancar. Es **idempotente**: si el
email ya existe, no modifica nada.

| Email | Contraseña | Rol | Extra |
|---|---|---|---|
| `admin@cinasee.com` | `Admin123!` | `ADMIN` | — |
| `staff@cinasee.com` | `Staff123!` | `STAFF_TAQUILLA` | `cinemaId = 550e8400-e29b-41d4-a716-446655440001`, `cinemaManager = true` |
| `usuario@cinasee.com` | `Usuario123!` | `CLIENTE_REGISTRADO` | — |

Se desactiva con `APP_SEED_ENABLED=false` (recomendado en producción).

El `cinemaId` del staff se toma de `APP_SEED_STAFF_CINEMA_ID`. El
`docker-compose.yml` raíz lo fija al UUID del cine de Madrid que crea el seed de
C1, de modo que la taquilla y la revisión de solicitudes de rol funcionan sin
configuración extra. Si cambias de cine, usa el UUID real de C1
(`GET /api/v1/cinemas`). Como el seed es idempotente, un volumen creado con un
valor anterior no se actualiza: hay que arrancar con
`iniciar.ps1 -BorrarDatos` (o `iniciar.sh --borrar-datos`) una vez.

---

## 9. Rate limit de invitados (Redis)

`POST /auth/guest` está limitado por dispositivo:

- Clave Redis: `rate:guest:<deviceId>`
- Ventana fija de **60 s** con máximo **10** peticiones
- Al superar el límite devuelve **429**

Si Redis no está disponible, el limitador registra un aviso y **permite** la
petición (*fail-open*): el servicio sigue funcionando, pero sin límite. Es una
decisión deliberada para no bloquear el desarrollo si la caché cae.

---

## 10. RabbitMQ y fidelización

El Componente 2 publica `PaymentCompleted` en el exchange `payment.exchange` con
routing key `payment.completed`. Este componente lo consume desde su propia cola:

| Elemento | Valor |
|---|---|
| Exchange | `payment.exchange` |
| Cola | `user.payment.completed` |
| Routing key | `payment.completed` |
| DLX / DLQ | `payment.exchange.dlx` / `user.payment.completed.dlq` |

Payload que consume este componente:

```json
{
  "eventId": "3f2c7a1e-...",
  "reservationId": 8901,
  "userId": 42,
  "movieId": "550e8400-e29b-41d4-a716-446655440001",
  "amount": 18.50,
  "currency": "EUR",
  "purchaseDate": "2026-09-20T18:30:00Z",
  "occurredAt": "2026-09-20T18:30:05Z"
}
```

El mismo mensaje incluye campos extra para el Componente 4 (`roomId`,
`sessionId`, `ticketsCount`, `totalAmount`, `timestamp`). Cada consumidor
deserializa solo los campos que conoce e ignora el resto.

Si `userId` es `null` (compra de invitado) no se asignan puntos ni se guarda
historial. Los reintentos están limitados a 3 intentos; después el mensaje va a
la DLQ (`default-requeue-rejected: false`).

---

## 11. Configuración

| Variable | Por defecto | Uso |
|---|---|---|
| `SERVER_PORT` | `8083` | Puerto |
| `MYSQL_HOST` / `MYSQL_PORT` | `localhost` / `3306` | MySQL |
| `MYSQL_DATABASE` | `cinasee_users_db` | Base de datos |
| `MYSQL_USER` / `MYSQL_PASSWORD` | `root` / vacío | Credenciales |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | Broker |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | `guest` / `guest` | Credenciales del broker |
| `JWT_ISSUER` | `http://localhost:8083/v1/iam` | Claim `iss` |
| `JWT_EXPIRATION_SECONDS` | `3600` | Vida del token |
| `JWT_GUEST_EXPIRATION_SECONDS` | `900` | Vida del token de invitado |
| `GUEST_RATE_LIMIT_MAX` | `10` | Peticiones por ventana |
| `GUEST_RATE_LIMIT_WINDOW` | `60` | Ventana en segundos |
| `APP_SEED_ENABLED` | `true` | Activa el seed |
| `APP_SEED_STAFF_CINEMA_ID` | UUID de Madrid | Cine del staff (coincide con el seed de C1) |
| `APP_SEED_*` | ver seed | Credenciales y cine del staff |

> El `iss` se mantiene como `http://localhost:8083/...` incluso dentro de Docker:
> es el valor que queda escrito en el token y el que validan los demás
> servicios. La descarga del JWKS sí usa el nombre interno del contenedor.

---

## 12. Decisiones y limitaciones conocidas

- **RS256 con par de claves efímero:** no se commitea ninguna clave privada,
  pero al reiniciar el servicio caducan los tokens. En producción: KMS/keystore.
- **Sin Spring Boot Actuator:** `SecurityConfig` permite `/actuator/health`,
  pero la dependencia no está incluida, así que ese endpoint no existe. El
  healthcheck del compose se basa en el arranque del proceso.
- **Rate limit *fail-open*:** prioriza disponibilidad sobre protección si Redis
  cae.
- **Cines modelados, pero `cinemaId` sin validar:** el catálogo (Componente 1)
  ya tiene entidad `Cinema` y `Room.cinemaId`, pero el `cinemaId` de usuarios es
  un número libre que asigna un `ADMIN`; no se comprueba contra C1. Si se
  quiere validar, C3 necesitaría una llamada a `GET /cinemas/{id}`.
- **`cinemaManager` solo por seed:** no hay endpoint para concederlo. Un
  `STAFF_TAQUILLA` aprobado por `POST /role-requests` no podrá revisar
  solicitudes hasta que se cambie el flag directamente en la base de datos.
- **Contraseñas:** BCrypt con el `PasswordEncoder` de Spring Security.
- **Sin refresh tokens:** al caducar, el cliente debe volver a hacer login.
# Migración de IDs de Cartelera

Los IDs de cine y película recibidos desde C1 se almacenan como texto. Antes de
desplegar esta versión sobre una base existente, detén el servicio, haz un
backup verificado y ejecuta
[`docs/migrations/001-catalog-ids-to-varchar.sql`](docs/migrations/001-catalog-ids-to-varchar.sql)
en MySQL. Los valores históricos se conservan como cadenas decimales; la
migración no reasigna IDs.
