# Entorno local: MySQL, Redis y RabbitMQ

El microservicio **no crea la base de datos**, solo las tablas. Hibernate está
configurado con `ddl-auto: update`, así que necesita que `cinasee_users_db`
exista antes de arrancar.

## Opción recomendada: Docker

Docker Desktop ya está instalado en el equipo, pero el motor debe estar
arrancado (icono de Docker Desktop en ejecución). Después, desde PowerShell:

### MySQL

```powershell
docker run -d --name cinasee-mysql `
  -p 3306:3306 `
  -e MYSQL_ROOT_PASSWORD=root `
  -e MYSQL_DATABASE=cinasee_users_db `
  -v cinasee-mysql-data:/var/lib/mysql `
  mysql:8.4
```

- `MYSQL_DATABASE` crea la base de datos automáticamente.
- `-v cinasee-mysql-data:/var/lib/mysql` hace que los datos sobrevivan si borras
  el contenedor.
- La aplicación se conecta a `jdbc:mysql://localhost:3306/cinasee_users_db`.

> **¿Dónde está la base de datos?** Dentro del volumen `cinasee-mysql-data`, que
> gestiona Docker (normalmente en la máquina virtual de WSL2). No es una carpeta
> del proyecto y no debes editarla a mano. Lo que sí está en el proyecto es el
> *esquema* y las tablas, que Hibernate crea al arrancar.

Comprobar que está lista:

```powershell
docker logs -f cinasee-mysql
# espera a ver: "ready for connections"
```

Crear la base de datos a mano (si no usaste `MYSQL_DATABASE`):

```powershell
docker exec -it cinasee-mysql mysql -uroot -proot `
  -e "CREATE DATABASE IF NOT EXISTS cinasee_users_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

### Redis

```powershell
docker run -d --name cinasee-redis `
  -p 6379:6379 `
  -v cinasee-redis-data:/data `
  redis:7-alpine
```

Redis **no guarda usuarios**. En este componente está reservado para
funcionalidades posteriores: revocación de JWT por `jti`, caché y control de
sesiones. Ahora mismo la aplicación puede arrancar sin él.

### RabbitMQ (necesario para la Fase 6)

```powershell
docker run -d --name cinasee-rabbitmq `
  -p 5672:5672 -p 15672:15672 `
  -e RABBITMQ_DEFAULT_USER=guest `
  -e RABBITMQ_DEFAULT_PASS=guest `
  -v cinasee-rabbitmq-data:/var/lib/rabbitmq `
  rabbitmq:4-management
```

- `5672`: puerto AMQP que usa Spring.
- `15672`: consola web en <http://localhost:15672> (`guest` / `guest`).

### Arrancar y parar los contenedores

```powershell
docker start cinasee-mysql cinasee-redis cinasee-rabbitmq
docker stop  cinasee-mysql cinasee-redis cinasee-rabbitmq
```

### Ejecutar la aplicación con la contraseña de Docker

El `application.yaml` usa `MYSQL_PASSWORD` con valor vacío por defecto. Con el
contenedor anterior la contraseña de root es `root`, así que:

```powershell
$env:MYSQL_PASSWORD="root"
.\mvnw.cmd spring-boot:run
```

Variables disponibles:

| Variable | Valor por defecto | Uso |
|---|---|---|
| `MYSQL_HOST` | `localhost` | Host de MySQL |
| `MYSQL_PORT` | `3306` | Puerto de MySQL |
| `MYSQL_DATABASE` | `cinasee_users_db` | Nombre de la base de datos |
| `MYSQL_USER` | `root` | Usuario |
| `MYSQL_PASSWORD` | vacío | Contraseña |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | RabbitMQ |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | `guest` / `guest` | RabbitMQ |
| `JWT_ISSUER` | `http://localhost:8083/v1/iam` | Emisor del JWT |
| `JWT_EXPIRATION_SECONDS` | `3600` | TTL del token de usuario |
| `JWT_GUEST_EXPIRATION_SECONDS` | `900` | TTL del token de invitado |
| `LOYALTY_POINTS_PER_EURO` | `1` | Puntos por euro |

## Opción sin Docker: instalación nativa

1. **MySQL**: instala *MySQL Community Server* con *MySQL Installer*. El
   instalador crea el servicio de Windows y guarda los datos en
   `C:\ProgramData\MySQL\MySQL Server X.Y\Data\`. Crea la base de datos:

   ```sql
   CREATE DATABASE cinasee_users_db
     CHARACTER SET utf8mb4
     COLLATE utf8mb4_unicode_ci;
   ```

2. **Redis en Windows**: Redis no tiene soporte oficial nativo. Usa Memurai o
   WSL2 (`wsl --install`, luego `sudo apt install redis-server`). Para el
   proyecto basta con que escuche en `localhost:6379`.

3. **RabbitMQ en Windows**: instala Erlang y después RabbitMQ Server. La consola
   de gestión se habilita con:

   ```powershell
   rabbitmq-plugins enable rabbitmq_management
   ```

## Comprobar que todo está levantado

```powershell
Test-NetConnection localhost -Port 3306
Test-NetConnection localhost -Port 6379
Test-NetConnection localhost -Port 5672
```

Los tres deben devolver `TcpTestSucceeded : True`.

## Nota para Docker Compose / otros componentes

Cuando los microservicios corran en contenedores, `localhost` ya no apunta al
host. Habrá que usar los nombres de servicio (`mysql`, `redis`, `rabbitmq`) o
`host.docker.internal` en Windows. Eso se resolverá cuando se añada el
`docker-compose.yml`, que de momento queda pendiente.
