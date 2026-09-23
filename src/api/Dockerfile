# =========================================================================
# ETAPA 1: Construcción (Build Stage)
# =========================================================================
FROM maven:3.9-eclipse-temurin-17 AS builder

# Establecemos el directorio de trabajo dentro del contenedor
WORKDIR /app

# Copiamos el manifiesto de dependencias primero para aprovechar el caché de capas de Docker
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copiamos el código fuente de la API
COPY src ./src

# Ejecutamos la compilación empaquetando el .jar ejecutable de Spring Boot
RUN mvn clean package -DskipTests

# =========================================================================
# ETAPA 2: Ejecución (Runtime Stage)
# =========================================================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copiamos únicamente el artefacto binario generado en la Etapa 1
COPY --from=builder /app/target/api-controlador-1.0-SNAPSHOT.jar app.jar

# Exponemos el puerto de la API REST (coincide con el mapeo del docker-compose)
EXPOSE 8080

# Punto de entrada inmutable para levantar el servicio Spring Boot
ENTRYPOINT ["java", "-jar", "app.jar"]