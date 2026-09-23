# Etapa 1: Compilación (Build Stage)
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /app

# Copiamos todo el repositorio para que Maven detecte la estructura completa del padre y sus hijos
COPY . .

# Compilamos exclusivamente el módulo 'api' y sus dependencias (-am)
RUN mvn clean package -pl api -am

# Etapa 2: Ejecución (Run Stage)
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Extraemos solo el ejecutable de la API para mantener la imagen limpia y liviana
COPY --from=builder /app/api/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]