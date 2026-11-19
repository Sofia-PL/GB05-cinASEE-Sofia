# Componente 5: Gateway

## Propósito

El componente 5 es la puerta de entrada HTTP a los servicios de CinAsee. Recibe
las solicitudes del cliente, las envía al componente que corresponde según la
ruta y valida los tokens JWT en las rutas protegidas. También configura el
acceso CORS para el frontend.

Está desarrollado con Spring Boot y Spring Cloud Gateway Server WebMVC. La
clase `Componente5GatewayApplication` inicia la aplicación; por defecto, el
gateway escucha en el puerto `8080`.

## Enrutamiento

Las rutas se definen en `src/main/resources/application.yaml`. El gateway
conserva la ruta y el método HTTP al reenviar la solicitud:

| Rutas | Servicio de destino | Puerto local predeterminado |
| --- | --- | --- |
| `/api/v1/cinemas/**`, `/api/v1/movies/**`, `/api/v1/rooms/**`, `/api/v1/sessions/**` | Componente 1: cartelera | `8081` |
| `/v1/reservations/**` | Componente 2: reservas | `8082` |
| `/v1/iam/**` | Componente 3: usuarios e identidad | `8083` |
| `/api/metrics/**`, `/api/reports/**`, `/api/recommendations/**` | Componente 4: analítica | `8084` |
| `/media/**` | Servicio `media-server` | `80` dentro de Docker |

Las rutas que no coinciden con ninguna de estas configuraciones no se reenvían
a los componentes; continúan por el manejo normal de Spring y, si no hay otro
manejador, responden con `404`.

## Flujo de autenticación

`JwtAuthenticationFilter` se ejecuta antes de procesar cada solicitud:

1. Deja pasar las solicitudes `OPTIONS` para permitir las comprobaciones
   preliminares de CORS.
2. No exige token para rutas que el gateway no tiene configuradas.
3. Permite sin token estas rutas públicas:
   - `GET /api/v1/movies/**`
   - `GET /api/v1/cinemas/**`
   - `GET /api/v1/sessions`
   - `GET /v1/reservations/shows/*/seats`
   - `GET /v1/iam/.well-known/**`
   - `GET /v1/iam/actuator/health`
   - `/v1/iam/auth/login`, `/v1/iam/auth/register` y
     `/v1/iam/auth/guest` (cualquier método HTTP).
4. Para las demás rutas configuradas, exige un único encabezado
   `Authorization: Bearer <token>`.
5. Valida la firma del JWT usando el JWKS del componente 3 y comprueba el
   emisor (`issuer`). Si falta el token o no es válido, devuelve `401` con el
   encabezado `WWW-Authenticate: Bearer`. Si es válido, la solicitud continúa
   hacia su servicio de destino con el encabezado de autorización.

El gateway valida la autenticidad del token, pero no aplica permisos por rol
ni reglas de autorización específicas de cada operación; esas reglas
corresponden a los servicios.

`JwtConfig` configura el decodificador JWT y sus validadores. `JwtUtil`
centraliza la decodificación, y `SecurityConfig` desactiva CSRF y permite que
las solicitudes lleguen al filtro y al enrutamiento del gateway.

## CORS

`CorsConfig` acepta solicitudes desde un origen configurado, permite los
métodos `GET`, `POST`, `PUT`, `DELETE` y `OPTIONS`, acepta cualquier encabezado
y permite credenciales. El origen predeterminado es `http://localhost:3000`.

## Configuración

Las variables de entorno permiten adaptar el componente a cada entorno:

| Variable | Valor predeterminado | Uso |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | Puerto del gateway |
| `COMPONENTE1_URI` | `http://localhost:8081` | URL del servicio de cartelera |
| `COMPONENTE2_URI` | `http://localhost:8082` | URL del servicio de reservas |
| `COMPONENTE3_URI` | `http://localhost:8083` | URL del servicio de usuarios e identidad |
| `COMPONENTE4_URI` | `http://localhost:8084` | URL del servicio de analítica |
| `GATEWAY_CORS_ALLOWED_ORIGIN` | `http://localhost:3000` | Origen autorizado para CORS |
| `JWT_JWK_SET_URI` | `http://localhost:8083/v1/iam/.well-known/jwks.json` | URL pública de claves para validar JWT |
| `JWT_ISSUER` | `http://localhost:8083/v1/iam` | Emisor esperado en el JWT |
| `MEDIA_URI` | `http://media-server:80` | URL interna del servidor de pósteres |

## Integración con Docker Compose

El servicio `componente5-gateway` está definido en el `docker-compose.yml` de la
raíz del repositorio y construye su imagen con el `Dockerfile` de este módulo.
En la red interna de Docker, reenvía las solicitudes a los servicios mediante
sus nombres DNS de Compose (`componente1-cartelera` a `componente4-analitica`),
no mediante `localhost`. Espera a que los cuatro servicios estén iniciados.

El puerto `8080` del contenedor se publica como `127.0.0.1:8080` en el equipo
local. Por tanto, el frontend o un cliente local debe enviar sus solicitudes a
`http://localhost:8080`. En Compose, el gateway descarga las claves JWT desde
la dirección interna del componente 3, pero mantiene `http://localhost:8083/v1/iam`
como emisor esperado porque ese es el valor público incluido en los tokens.
El origen CORS predeterminado para este despliegue es `http://localhost:3000`.

Desde la raíz del repositorio, se puede iniciar el gateway y los servicios de
los que depende con:

```powershell
docker compose up --build componente5-gateway
```

Para iniciar toda la plataforma:

```powershell
docker compose up --build
```

## Ejecución y pruebas

Desde la carpeta `componente5-gateway`, con Java 21 instalado:

```powershell
.\mvnw.cmd spring-boot:run
```

Para ejecutar las pruebas automatizadas:

```powershell
.\mvnw.cmd test
```

Las pruebas de integración cubren el reenvío a los servicios, las rutas
públicas, la validación de tokens, CORS y el comportamiento de rutas no
configuradas.
