# =====================================================================
# Imagen del Componente 4 - Analítica (multi-stage build)
#
#   Etapa 1 (build): compila con Maven y JDK 21 dentro del contenedor, así no
#                    hace falta tener Java instalado para construir la imagen.
#   Etapa 2 (run):   solo el JRE 21 y el .jar → imagen más pequeña y sin
#                    herramientas de compilación.
#
# Construir:  docker build -t cinasee/componente4-analitica .
# =====================================================================

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 1) Primero solo el wrapper y el pom: Docker cachea la descarga de dependencias
#    y no la repite mientras el pom.xml no cambie.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# En Windows mvnw puede tener finales de línea CRLF; se normalizan a LF
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# 2) Después el código fuente y el empaquetado (los tests se ejecutan aparte con mvnw test)
COPY src/ src/
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

# El proceso no se ejecuta como root dentro del contenedor
RUN groupadd --system cinasee && useradd --system --gid cinasee cinasee
COPY --from=build /workspace/target/componente4-analitica-*.jar /app/app.jar
USER cinasee

EXPOSE 8084
# MaxRAMPercentage: la JVM ajusta la memoria al límite del contenedor
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
