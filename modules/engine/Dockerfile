# Etapa 1: Compilación (Build Stage)
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /app

# Copiamos todo el repositorio para que Maven detecte la estructura completa del POM padre y sus submódulos
COPY . .

# Compilamos exclusivamente el nuevo módulo 'engine' y sus dependencias (-am)
RUN mvn clean package -pl modules/engine -am

# Etapa 2: Ejecución (Run Stage)
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Extraemos solo el ejecutable del engine desde su nueva ruta para mantener la imagen limpia
COPY --from=builder /app/modules/engine/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]