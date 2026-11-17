# Despliegue en Docker del stack CinAsee

> Documento del **Componente 3 (Usuarios / IAM y Fidelización)**.
> Última actualización: 2026-11-09.
>
> Alcance: se han implementado los cambios del Componente 3 **y** las
> correcciones de los Componentes 1, 2 y 4 descritas en este documento. El
> `docker-compose.yml` global queda preparado para el stack completo.
> Siguen fuera de alcance el frontend (otra rama) y el API Gateway (sin
> implementar).

---

## 1. Resumen

### Lo que ya funciona

- El Componente 3 tiene `Dockerfile` multi-stage y `.dockerignore`.
- El compose global incluye:
  - `mysql-usuarios` (MySQL 8.4)
  - `redis-usuarios` (Redis 7)
  - `componente3-usuarios` (Spring Boot 4.1.2 / Java 21)
- Los volúmenes creados manualmente se reutilizan:
  - `cinasee-mysql-data`
  - `cinasee-redis-data`
- La imagen se construye correctamente:
  - `docker compose build componente3-usuarios` → OK
  - Imagen resultante: `cinasee-componente3-usuarios`
- Perfil real con `phone` y `homeCinema`; `PUT /v1/iam/users/{id}` ya persiste
  los cambios en MySQL.
- Usuarios semilla con roles reales: `admin@cinasee.com`,
  `staff@cinasee.com` y `usuario@cinasee.com`.
- Flujo de solicitudes de rol `STAFF_TAQUILLA` / `ADMIN` con aprobación por
  parte de un administrador global o del taquillero responsable del cine.
- Rate limiting de `/auth/guest` en Redis: 10 peticiones/minuto por `deviceId`.

### Cambios aplicados en el resto de componentes (2026-11-09)

- **Componente 1:** `jwk-set-uri` + `issuer-uri` con variables de entorno,
  Dockerfile multi-stage, `.dockerignore` y `GET /movies/{id}` público.
- **Componente 2:** compose aislado alineado (usuario RabbitMQ, puertos
  `127.0.0.1`, `restart`).
- **Componente 4:** dependencias de seguridad, Resource Server, reenvío de JWT
  por Feign, URL de Usuarios `/v1/iam`, Dockerfile multi-stage y `.dockerignore`.
- **RabbitMQ:** `rabbitmq:4-management` con usuario dedicado `cinasee`.
- **Compose global:** healthchecks, `depends_on` con `service_healthy`,
  puertos en `127.0.0.1`, `restart` y eliminación de `version`.

### Sigue fuera de alcance

- `componente5-gateway/**`: sin rutas ni seguridad implementadas.
- `frontend-ui/**`: se desarrolla en otra rama.

> **Importante:** al cambiar el usuario de RabbitMQ hay que recrear el volumen
> `cinasee_rabbitmq_data` (ver sección 8). Los volúmenes de MySQL y Redis se
> conservan.

---

## 2. Arquitectura en Docker

```text
                         Red del compose (cinasee_default)
┌─────────────────────────────────────────────────────────────────────────┐
│                                                                         │
│  componente3-usuarios :8083                                             │
│      ├── mysql-usuarios:3306 ──► volumen cinasee-mysql-data            │
│      ├── redis-usuarios:6379 ──► volumen cinasee-redis-data            │
│      └── rabbitmq:5672        ──► exchange payment.exchange             │
│                                                                         │
│  componente2-reservas :8082 ──publica──► rabbitmq                       │
│  componente4-analitica :8084 ──consume──► rabbitmq                      │
│  componente1-cartelera :8081 ──MongoDB──► mongodb-cartelera             │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

| Servicio | Imagen | Puerto host | Volumen |
|---|---|---|---|
| `mysql-usuarios` | `mysql:8.4` | `127.0.0.1:3306` | `cinasee-mysql-data` |
| `redis-usuarios` | `redis:7-alpine` | `127.0.0.1:6379` | `cinasee-redis-data` |
| `componente3-usuarios` | build local | `127.0.0.1:8083` | — |
| `rabbitmq` | `rabbitmq:4-management` | `127.0.0.1:5672`, `127.0.0.1:15672` | `rabbitmq_data` |

> Los puertos se publican solo en `127.0.0.1` para no exponer las bases de datos
> a la red local.

---

## 3. Cambios realizados en el Componente 3

### 3.1. `componente3-usuarios/Dockerfile`

Build en dos etapas:

1. **build**: `eclipse-temurin:21-jdk`, descarga dependencias con
   `dependency:go-offline`, copia `openapi/` (necesario para el generador) y
   `src/`, y empaqueta con `-DskipTests`.
2. **run**: `eclipse-temurin:21-jre`, usuario no root `cinasee`,
   `-XX:MaxRAMPercentage=75`.

Detalles importantes:

- Se copia `openapi/` **antes** de `src/` porque el plugin
  `openapi-generator-maven-plugin` lee `openapi/componente_3.yaml` en
  `generate-sources`.
- Se normaliza `mvnw` de CRLF a LF (`sed -i 's/\r$//'`) para que funcione en
  Linux aunque el repositorio se haya clonado en Windows.
- Los tests no se ejecutan dentro de la imagen; se ejecutan aparte con
  `.\mvnw.cmd test`.

### 3.2. `componente3-usuarios/.dockerignore`

Excluye `target/`, `docs/` y archivos de IDE. **No excluye `openapi/`**, porque
el build lo necesita.

### 3.3. `docker-compose.yml` global

Se ha añadido, bajo el comentario `COMPONENTE 3: USUARIOS`:

- `mysql-usuarios`
- `redis-usuarios`
- `componente3-usuarios`

Y en `volumes:`:

```yaml
  mysql_usuarios_data:
    name: cinasee-mysql-data
  redis_usuarios_data:
    name: cinasee-redis-data
```

El `name:` hace que Compose use exactamente los volúmenes creados con
`docker run`, por lo que **los datos actuales se conservan**.

Variables del Componente 3:

| Variable | Valor en Docker | Uso |
|---|---|---|
| `MYSQL_HOST` | `mysql-usuarios` | Host interno de MySQL |
| `MYSQL_PORT` | `3306` | Puerto interno |
| `MYSQL_DATABASE` | `cinasee_users_db` | Base de datos |
| `MYSQL_USER` | `cinasee` | Usuario de la app |
| `MYSQL_PASSWORD` | `cinasee` | Contraseña |
| `REDIS_HOST` | `redis-usuarios` | Host interno de Redis |
| `REDIS_PORT` | `6379` | Puerto interno |
| `RABBITMQ_HOST` | `rabbitmq` | Broker compartido |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | `cinasee` / `cinasee` | Credenciales del broker |
| `JWT_ISSUER` | `http://localhost:8083/v1/iam` | Claim `iss` de los tokens |

> `JWT_ISSUER` se mantiene en `localhost` a propósito: es el valor que validan
> los Componentes 1, 2 y 4. El JWKS se descarga por la red interna con
> `http://componente3-usuarios:8083/v1/iam/.well-known/jwks.json`.

---

## 4. Cómo arrancar solo el Componente 3

### 4.1. Liberar los puertos de los contenedores manuales

Los contenedores `cinasee-mysql` y `cinasee-redis` que creaste con `docker run`
siguen ocupando `3306` y `6379`. Hay que pararlos y eliminarlos, pero **no** sus
volúmenes:

```powershell
docker stop cinasee-mysql cinasee-redis
docker rm cinasee-mysql cinasee-redis
```

Comprobar que los volúmenes siguen ahí:

```powershell
docker volume ls | Select-String "cinasee"
```

### 4.2. Construir y levantar

```powershell
docker compose config
docker compose build componente3-usuarios
docker compose up -d componente3-usuarios
```

`docker compose up -d componente3-usuarios` arranca también sus dependencias:

- `mysql-usuarios`
- `redis-usuarios`
- `rabbitmq`

No arranca los Componentes 1, 2 ni 4.

### 4.3. Comprobar

```powershell
docker compose ps
docker compose logs -f componente3-usuarios
```

Espera a ver algo como:

```text
Started Componente3UsuariosApplication
Tomcat started on port 8083
```

Para salir del log: `Ctrl+C` (no para el contenedor).

---

## 5. Verificación funcional

### 5.1. JWKS

```powershell
Invoke-RestMethod http://localhost:8083/v1/iam/.well-known/jwks.json
```

### 5.2. Registro y login

```powershell
$body = @{
  email = "cliente@cinasee.com"
  password = "Password123!"
  fullName = "María Gómez"
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8083/v1/iam/auth/register" `
  -ContentType "application/json" -Body $body

$login = @{
  email = "cliente@cinasee.com"
  password = "Password123!"
} | ConvertTo-Json

$token = (Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8083/v1/iam/auth/login" `
  -ContentType "application/json" -Body $login).accessToken
```

Usuarios de demostración creados por `DemoDataInitializer` (solo si no existen):

| Email | Contraseña | Rol | `cinemaId` | `cinemaManager` |
|---|---|---|---|---|
| `admin@cinasee.com` | `Admin123!` | `ADMIN` | — | `false` |
| `staff@cinasee.com` | `Staff123!` | `STAFF_TAQUILLA` | `1` | `true` |
| `usuario@cinasee.com` | `Usuario123!` | `CLIENTE_REGISTRADO` | — | `false` |

Se desactivan con `APP_SEED_ENABLED=false`. Los tres sirven para el acceso
rápido del frontend y devuelven un JWT real del Componente 3.

### 5.3. MySQL

```powershell
docker exec -it cinasee-mysql-usuarios mysql -ucinasee -pcinasee cinasee_users_db `
  -e "SHOW TABLES;"

docker exec -it cinasee-mysql-usuarios mysql -ucinasee -pcinasee cinasee_users_db `
  -e "SELECT id, email, full_name, role, loyalty_points FROM users;"
```

Deben existir:

- `users`
- `user_preferred_genres`
- `purchase_history`
- `role_requests`

### 5.4. Redis

```powershell
docker exec -it cinasee-redis-usuarios redis-cli ping
# PONG
```

Redis se usa para el rate limiting de invitados. Tras varias peticiones a
`/auth/guest` con el mismo `deviceId` aparecerán claves `rate:guest:<deviceId>`:

```powershell
docker exec -it cinasee-redis-usuarios redis-cli keys "rate:guest:*"
```

### 5.5. RabbitMQ

Consola web: <http://localhost:15672> (`cinasee` / `cinasee`).

Deben existir:

- exchange `payment.exchange`
- cola `user.payment.completed`
- DLQ `user.payment.completed.dlq`
- exchange `payment.exchange.dlx`

Si el Componente 3 no puede autenticarse contra RabbitMQ, ver la sección 6.3.

### 5.6. Perfil con JWT

```powershell
$headers = @{ Authorization = "Bearer $token" }

Invoke-RestMethod -Uri "http://localhost:8083/v1/iam/users/1" -Headers $headers

$profile = @{
  fullName = "Usuario Demo"
  phone = "+34 600 000 000"
  homeCinema = "CineAsee Centro"
  preferredGenres = @("ACCION", "COMEDIA")
} | ConvertTo-Json

Invoke-RestMethod -Method Put -Uri "http://localhost:8083/v1/iam/users/1" `
  -Headers $headers -ContentType "application/json" -Body $profile
```

`UserProfile` devuelve además `phone`, `homeCinema`, `cinemaId` y
`canReviewRoleRequests`. Este último indica al frontend si debe mostrar el panel
de revisión de solicitudes.

---

## 6. Correcciones aplicadas al resto del proyecto

> Las subsecciones siguientes describen cada problema y la solución que ya está
> implementada en el repositorio.

### 6.1. Componente 1 — Cartelera

#### 6.1.1. `issuer-uri` apunta a `localhost` dentro del contenedor

**Archivo:** `componente1-cartelera/src/main/resources/application.yaml`

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8083/v1/iam
```

**Problema:** `issuer-uri` hace que Spring Security descargue la configuración
OIDC al arrancar. Dentro del contenedor, `localhost:8083` es el propio
Componente 1, no el Componente 3. El contenedor falla al iniciar.

**Solución recomendada:** usar `jwk-set-uri` para descargar la clave pública por
la red de Docker y mantener `issuer-uri` con el valor del claim `iss`.

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${JWT_JWK_SET_URI:http://localhost:8083/v1/iam/.well-known/jwks.json}
          issuer-uri: ${JWT_ISSUER:http://localhost:8083/v1/iam}
```

Y en el compose:

```yaml
  componente1-cartelera:
    environment:
      SPRING_DATA_MONGODB_URI: mongodb://mongodb-cartelera:27017/cine_catalog_db
      SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI: http://componente3-usuarios:8083/v1/iam/.well-known/jwks.json
      SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI: http://localhost:8083/v1/iam
```

Con `jwk-set-uri` presente, Spring Boot no hace discovery al arrancar: descarga
el JWKS de forma perezosa cuando llega el primer token.

#### 6.1.2. El Dockerfile depende de un JAR ya compilado

**Archivo:** `componente1-cartelera/Dockerfile`

```dockerfile
COPY target/*.jar app.jar
```

**Problema:** en un clon limpio, `target/` no existe y
`docker compose build` falla.

**Solución:** convertir a multi-stage como el Componente 2 o compilar antes:

```powershell
cd componente1-cartelera
.\mvnw.cmd -DskipTests package
```

Se recomienda multi-stage:

```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system cinasee && useradd --system --gid cinasee cinasee
COPY --from=build /workspace/target/componente1-cartelera-*.jar /app/app.jar
USER cinasee
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
```

#### 6.1.3. Endpoints que consume el Componente 4

`GET /movies/{id}` se ha dejado **público** para que el listener de RabbitMQ del
Componente 4 pueda enriquecer el evento sin JWT (no hay petición HTTP entrante).
`GET /rooms`, `GET /rooms/{id}` y el resto siguen requiriendo JWT; el Componente
4 los llama dentro de peticiones HTTP y reenvía el token. Ver sección 6.2.3.

---

### 6.2. Componente 4 — Analítica

#### 6.2.1. URL de Usuarios incorrecta

**Archivo:** `componente4-analitica/src/main/java/es/cineasee/analitica/clients/UsuariosClient.java`

```java
@FeignClient(name = "usuarios-service",
             url = "${services.usuarios.url:http://localhost:8083/api/v1}")
```

**Problema:** el context-path del Componente 3 es `/v1/iam`, no `/api/v1`.

**Solución:**

```java
@FeignClient(name = "usuarios-service",
             url = "${services.usuarios.url:http://localhost:8083/v1/iam}")
```

Y en el compose:

```yaml
      USUARIOS_SERVICE_URL: http://componente3-usuarios:8083/v1/iam
```

#### 6.2.2. Variable de RabbitMQ distinta

**Archivo:** `componente4-analitica/src/main/resources/application.yml`

```yaml
  rabbitmq:
    username: ${RABBITMQ_USERNAME:guest}
```

Los Componentes 2 y 3 usan `RABBITMQ_USER`. Si se cambia el usuario del broker,
el Componente 4 seguirá intentando `guest`.

**Solución aplicada:** `application.yml` acepta `RABBITMQ_USER` y mantiene
`RABBITMQ_USERNAME` como alternativa:

```yaml
  rabbitmq:
    username: ${RABBITMQ_USER:${RABBITMQ_USERNAME:guest}}
```

Y en el compose se define la variable común:

```yaml
      RABBITMQ_USER: cinasee
      RABBITMQ_PASSWORD: cinasee
```

#### 6.2.3. Llamadas al Componente 1 y al Componente 3 sin JWT

**Problema:** `CarteleraClient` y `UsuariosClient` del Componente 4 no tienen
interceptor Feign. Los endpoints de Cartelera y Usuarios exigen JWT, así que
responden `401`. `AnalyticsService` captura la excepción y devuelve datos de
respaldo o un `404` en `/recommendations`.

**Solución:** copiar el patrón del Componente 2.

1. Añadir dependencias al `pom.xml`:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

2. Crear `ForwardJwtFeignConfig` (igual que en el Componente 2) y aplicarlo:

```java
@FeignClient(name = "cartelera-service",
             url = "${services.cartelera.url}",
             configuration = ForwardJwtFeignConfig.class)
public interface CarteleraClient { ... }

@FeignClient(name = "usuarios-service",
             url = "${services.usuarios.url}",
             configuration = ForwardJwtFeignConfig.class)
public interface UsuariosClient { ... }
```

3. Añadir un `SecurityConfig` con `oauth2ResourceServer(jwt)` y proteger los
   endpoints que correspondan. Como mínimo:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${JWT_JWK_SET_URI:http://localhost:8083/v1/iam/.well-known/jwks.json}
          issuer-uri: ${JWT_ISSUER:http://localhost:8083/v1/iam}
```

Y en el compose:

```yaml
      JWT_JWK_SET_URI: http://componente3-usuarios:8083/v1/iam/.well-known/jwks.json
      JWT_ISSUER: http://localhost:8083/v1/iam
```

> Sin este cambio, `/recommendations/users/{id}` no funcionará aunque el
> Componente 3 esté levantado.

#### 6.2.4. Dockerfile dependiente de `target/*.jar`

Igual que el Componente 1: convertir a multi-stage o compilar antes.

#### 6.2.5. PostgreSQL sin healthcheck

**Archivo:** `docker-compose.yml` (servicio `postgres-analitica`)

**Solución:**

```yaml
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres -d cine_analytics_dw"]
      interval: 5s
      timeout: 3s
      retries: 10
```

Y en `componente4-analitica`:

```yaml
    depends_on:
      postgres-analitica:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
      componente3-usuarios:
        condition: service_started
```

#### 6.2.6. Contrato del evento

El Componente 2 publica `totalAmount` como `BigDecimal` y el Componente 4 lo
espera como `Double`. Jackson lo convierte sin problema, pero conviene
homologarlo para evitar sorpresas si se activa una validación estricta.

---

### 6.3. RabbitMQ

#### 6.3.1. `guest` restringido a loopback

RabbitMQ limita el usuario `guest` a conexiones desde `localhost`. Dentro de
Docker, los demás contenedores no llegan como `localhost`, así que puede
aparecer:

```text
ACCESS_REFUSED - Login was refused using authentication mechanism PLAIN
```

**Solución aplicada:** usuario dedicado en el servicio compartido:

```yaml
  rabbitmq:
    image: rabbitmq:4-management
    environment:
      RABBITMQ_DEFAULT_USER: cinasee
      RABBITMQ_DEFAULT_PASS: cinasee
```

Y actualizados los tres consumidores/productores:

- Componente 2: `RABBITMQ_USER` / `RABBITMQ_PASSWORD`
- Componente 3: `RABBITMQ_USER` / `RABBITMQ_PASSWORD`
- Componente 4: `RABBITMQ_USER` / `RABBITMQ_PASSWORD`

> El usuario `RABBITMQ_DEFAULT_USER` solo se crea al inicializar el volumen. Si
> el volumen `rabbitmq_data` ya existía con `guest`, hay que recrearlo (ver
> sección 8).

Si no se puede cambiar el broker, la alternativa es desactivar la restricción:

```yaml
      RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS: "-rabbitmq loopback_users.guest false"
```

No se recomienda en producción.

#### 6.3.2. Versiones distintas

- Estado anterior: compose global con `rabbitmq:3-management-alpine` y compose
  aislado del Componente 2 con `rabbitmq:4-management`.
- **Solución aplicada:** ambos usan `rabbitmq:4-management`, con el mismo
  usuario `cinasee`.

#### 6.3.3. Dos brokers a la vez

El compose global y el compose aislado del Componente 2 publican los mismos
puertos (`5672`, `15672`, `8082`, `5433`). No deben ejecutarse a la vez.

- Stack completo: `docker compose up -d` en la raíz.
- Pruebas aisladas del Componente 2: `docker compose up -d` en
  `componente2-reservas/`.

---

### 6.4. Compose global: mejoras generales

| Problema | Corrección |
|---|---|
| `version: '3.8'` obsoleto | Eliminar la línea |
| Puertos en `0.0.0.0` | Usar `127.0.0.1:puerto:puerto` |
| `depends_on` solo con `service_started` | Añadir healthchecks y `condition: service_healthy` |
| Sin `restart` | Añadir `restart: unless-stopped` |
| Mongo sin healthcheck | `mongosh --quiet --eval "db.adminCommand('ping')"` |
| RabbitMQ sin healthcheck | `rabbitmq-diagnostics -q ping` |
| PostgreSQL de analítica sin healthcheck | `pg_isready -U postgres -d cine_analytics_dw` |

Ejemplo de healthcheck de Mongo:

```yaml
    healthcheck:
      test: ["CMD", "mongosh", "--quiet", "--eval", "db.adminCommand('ping')"]
      interval: 10s
      timeout: 5s
      retries: 10
```

---

### 6.5. Pendientes del Componente 3

| Tema | Estado | Recomendación |
|---|---|---|
| Actuator | `SecurityConfig` permite `/actuator/health`, pero no está la dependencia | Añadir `spring-boot-starter-actuator` y usarlo en el healthcheck |
| Clave RSA | Se genera en cada arranque | Persistir en keystore/KMS; si no, reiniciar invalida los tokens |
| `ddl-auto` | `update` | Migrar a Flyway con `validate` para despliegues reproducibles |
| Redis | Rate limiting de invitados implementado | Añadir revocación de JWT por `jti` o caché de sesiones |
| CAPTCHA de invitados | Pendiente: no hay proveedor configurado | Integrar un proveedor y validar `captchaToken` antes de emitir el JWT |
| Usuarios semilla | Contraseñas de demo en `application.yaml` | Mantener `APP_SEED_ENABLED=false` en producción |
| Cambio de rol | El JWT antiguo conserva el rol anterior | El frontend debe forzar un nuevo login tras la aprobación |
| Discovery OIDC | `jwks_uri` apunta a `localhost` | Usar `jwk-set-uri` en los demás servicios; no usar discovery dentro de Docker |
| Tests en imagen | Se compilan con `-DskipTests` | Ejecutar `.\mvnw.cmd test` en CI antes de construir |

---

### 6.6. Fuera de alcance

- **Frontend:** se está desarrollando en otra rama. No tiene Dockerfile ni
  llamadas reales a la API en esta rama.
- **API Gateway (Componente 5):** solo tiene `spring.application.name`; no hay
  rutas ni seguridad implementadas. No se containeriza todavía.

---

## 7. Matriz de responsabilidades

| Elemento | Responsable | Estado |
|---|---|---|
| MySQL, Redis, Comp. 3, Dockerfile y compose del Comp. 3 | Nosotros | Hecho |
| Comp. 1: JWT, Dockerfile, healthcheck de Mongo | Nosotros | Hecho |
| Comp. 2: usuario RabbitMQ, compose aislado | Nosotros | Hecho |
| Comp. 4: URL de Usuarios, JWT, Dockerfile, healthcheck | Nosotros | Hecho |
| RabbitMQ compartido: usuario dedicado y versión | Nosotros | Hecho |
| Frontend | Otra rama | Fuera de alcance |
| Gateway | Sin implementar | Fuera de alcance |

---

## 8. Arranque del stack completo

Las correcciones de las secciones 6.1 a 6.4 ya están aplicadas. Como el usuario
de RabbitMQ ha cambiado, hay que recrear **solo** el volumen del broker:

```powershell
# 1. Parar el stack (NO uses -v: borraría MySQL y Redis)
docker compose down

# 2. Ver el nombre real del volumen de RabbitMQ
docker volume ls | Select-String "rabbitmq"

# 3. Borrar solo ese volumen (las colas se recrean al arrancar)
docker volume rm cinasee_rabbitmq_data

# 4. Validar, construir y levantar
docker compose config
docker compose build
docker compose up -d
docker compose ps
```

> No borres `cinasee-mysql-data` ni `cinasee-redis-data`: contienen los datos
> que se quieren conservar.

Por último, revisar los logs de cada servicio:

```powershell
docker compose logs -f componente1-cartelera
docker compose logs -f componente2-reservas
docker compose logs -f componente3-usuarios
docker compose logs -f componente4-analitica
```

---

## 9. Pruebas de integración finales

### 9.1. Flujo de usuario

1. Registrar un usuario en el Componente 3.
2. Hacer login y obtener el JWT.
3. Consultar el perfil con el JWT.
4. Crear una reserva en el Componente 2 (con el JWT).
5. Pagar la reserva.
6. Comprobar:
   - puntos actualizados en el Componente 3,
   - fila en `purchase_history`,
   - fila en la tabla de analítica del Componente 4,
   - mensajes en las colas de RabbitMQ.

### 9.2. Evento `PaymentCompleted` manual

Publicar en `payment.exchange`, routing key `payment.completed`:

```json
{
  "eventId": "prueba-1",
  "reservationId": 999001,
  "userId": 1,
  "movieId": 1024,
  "amount": 18.50,
  "currency": "EUR",
  "purchaseDate": "2026-09-20T18:30:00Z",
  "occurredAt": "2026-09-20T18:30:05Z"
}
```

Comprobar en los logs del Componente 3:

```text
Reserva 999001: +18 puntos al usuario 1 (total 18)
```

Y en MySQL:

```powershell
docker exec -it cinasee-mysql-usuarios mysql -ucinasee -pcinasee cinasee_users_db `
  -e "SELECT * FROM purchase_history;"
```

### 9.3. Pruebas de los endpoints corregidos

```powershell
# Cartelera: listado y detalle públicos (sin JWT)
Invoke-RestMethod http://localhost:8081/api/v1/movies

# Login en IAM
$base = "http://localhost:8083/v1/iam"
$login = @{ email = "cliente@cinasee.com"; password = "Password123!" } | ConvertTo-Json
$token = (Invoke-RestMethod -Method Post -Uri "$base/auth/login" `
  -ContentType "application/json" -Body $login).accessToken
$headers = @{ Authorization = "Bearer $token" }

# Analítica: recomendaciones del propio usuario
Invoke-RestMethod -Uri "http://localhost:8084/api/recommendations/users/1" -Headers $headers

# Analítica: métricas (requiere STAFF_TAQUILLA o ADMIN)
Invoke-RestMethod -Uri "http://localhost:8084/api/metrics/occupancy?cinemaId=1" -Headers $headers

# Analítica: sin token debe devolver 401
try {
  Invoke-RestMethod -Uri "http://localhost:8084/api/reports/box-office"
} catch {
  $_.Exception.Response.StatusCode.value__   # 401
}
```

### 9.4. Flujo de solicitudes de rol

```powershell
$base = "http://localhost:8083/v1/iam"

# 1. Login del cliente que solicita el rol
$userLogin = @{ email = "usuario@cinasee.com"; password = "Usuario123!" } | ConvertTo-Json
$user = (Invoke-RestMethod -Method Post -Uri "$base/auth/login" `
  -ContentType "application/json" -Body $userLogin).accessToken
$userHeaders = @{ Authorization = "Bearer $user" }

# 2. Crear la solicitud
$roleRequest = @{
  requestedRole = "STAFF_TAQUILLA"
  cinemaId = 1
  motivation = "Trabajo en la taquilla del cine 1"
} | ConvertTo-Json

$created = Invoke-RestMethod -Method Post -Uri "$base/role-requests" `
  -Headers $userHeaders -ContentType "application/json" -Body $roleRequest
$created | ConvertTo-Json -Depth 5

# 3. Ver mis solicitudes
Invoke-RestMethod -Uri "$base/role-requests/me" -Headers $userHeaders |
  ConvertTo-Json -Depth 5

# 4. Login del ADMIN y listado de pendientes
$adminLogin = @{ email = "admin@cinasee.com"; password = "Admin123!" } | ConvertTo-Json
$admin = (Invoke-RestMethod -Method Post -Uri "$base/auth/login" `
  -ContentType "application/json" -Body $adminLogin).accessToken
$adminHeaders = @{ Authorization = "Bearer $admin" }

Invoke-RestMethod -Uri "$base/role-requests?status=PENDING&cinemaId=1" `
  -Headers $adminHeaders | ConvertTo-Json -Depth 5

# 5. Aprobar
$review = @{
  status = "APPROVED"
  reviewComment = "Aprobado por el administrador"
} | ConvertTo-Json

Invoke-RestMethod -Method Patch -Uri "$base/role-requests/$($created.id)" `
  -Headers $adminHeaders -ContentType "application/json" -Body $review |
  ConvertTo-Json -Depth 5

# 6. Volver a hacer login para que el JWT lleve el rol nuevo
$user = (Invoke-RestMethod -Method Post -Uri "$base/auth/login" `
  -ContentType "application/json" -Body $userLogin).accessToken
Invoke-RestMethod -Uri "$base/users/3" -Headers @{ Authorization = "Bearer $user" } |
  ConvertTo-Json -Depth 5
```

El `staff@cinasee.com` también puede revisar solicitudes `STAFF_TAQUILLA` de su
propio cine (`cinemaId=1`), pero no solicitudes `ADMIN` ni de otros cines.

### 9.5. Rate limit de invitados

```powershell
$guestBody = @{ deviceId = "web-demo-12345678" } | ConvertTo-Json

1..12 | ForEach-Object {
  try {
    Invoke-RestMethod -Method Post -Uri "$base/auth/guest" `
      -ContentType "application/json" -Body $guestBody | Out-Null
    "$_ OK"
  } catch {
    "$_ -> $($_.Exception.Response.StatusCode.value__)"
  }
}
```

Las 10 primeras peticiones devuelven `200`; a partir de la 11 se recibe `429`.
El contador vive en Redis y caduca a los 60 segundos.

---

## 10. Limitaciones conocidas

- El CAPTCHA de `/auth/guest` sigue pendiente: solo hay rate limiting por
  `deviceId`.
- Si Redis no está disponible, el rate limiting se desactiva y se registra un
  aviso para no bloquear por completo la emisión de tokens de invitado.
- Los usuarios semilla tienen contraseñas conocidas; desactivar el seed en
  producción.
- El rol viaja en el JWT: tras aprobar una solicitud hay que volver a iniciar
  sesión para obtener el rol nuevo.
- La clave RSA se regenera en cada arranque.
- `ddl-auto: update` no es apto para producción.
- La DLQ no tiene reintento automático ni alerta.
- El volumen de RabbitMQ debe recrearse si se cambia el usuario del broker.
- El listener de eventos del Componente 4 llama a Cartelera sin JWT; por eso
  `GET /movies/{id}` es público. El resto de llamadas sí reenvían el token.
- El frontend y el gateway quedan fuera de este despliegue.
