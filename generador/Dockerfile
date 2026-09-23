# Etapa 1: Compilación (Build Stage)
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /app

# Copiamos todo el repositorio raíz
COPY . .

# Compilamos exclusivamente el módulo 'generador' y sus dependencias (-am)
RUN mvn clean package -pl generador -am

# Etapa 2: Ejecución (Run Stage)
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Extraemos el JAR compilado desde la ruta correcta
COPY --from=builder /app/generador/target/*-jar-with-dependencies.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]