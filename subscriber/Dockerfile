# Etapa 1: Compilación (Build Stage)
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /app

# Copiamos todo el repositorio raíz para que Maven reconozca el POM padre y sus hijos
COPY . .

# Compilamos exclusivamente el módulo 'subscriber' y sus dependencias (-am)
RUN mvn clean package -pl subscriber -am

# Etapa 2: Ejecución (Run Stage)
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Extraemos el JAR compilado desde la ruta correcta
COPY --from=builder /app/subscriber/target/*-jar-with-dependencies.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]