FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
# Copiar el archivo JAR generado por Maven
COPY target/*.jar app.jar
# Exponer el puerto del microservicio
EXPOSE 8084
# Comando para ejecutar la aplicación
ENTRYPOINT ["java", "-jar", "app.jar"]

