# Etapa 1: Compilación (Build Stage)
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
# Copiamos el pom.xml y el código fuente
COPY pom.xml .
COPY src ./src
# Compilamos el proyecto empaquetando el .jar sin depender del host
RUN mvn clean package -DskipTests

# Etapa 2: Ejecución (Run Stage)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Traemos solo el ejecutable de la etapa anterior para mantener la imagen liviana
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]