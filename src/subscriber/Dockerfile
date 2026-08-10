# ==========================================
# ETAPA 1: Construcción (Build)
# ==========================================
# Usamos una imagen que contiene Maven y el JDK completo para poder compilar.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copiamos primero el pom.xml y el código fuente al contenedor
COPY pom.xml .
COPY src ./src

# Ejecutamos Maven para compilar el proyecto y generar el archivo .jar ejecutable.
# Usamos -DskipTests para acelerar el proceso omitiendo los tests (útil en esta etapa 1).
RUN mvn clean package -DskipTests

# ==========================================
# ETAPA 2: Ejecución (Run)
# ==========================================
# Para la imagen final, usamos solo el JRE (Java Runtime Environment), que es mucho más ligero.
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copiamos el archivo .jar generado en la ETAPA 1 (build) hacia esta imagen final limpia.
COPY --from=build /app/target/*-jar-with-dependencies.jar app.jar

# Comando principal que se ejecuta al levantar el contenedor por el docker-compose.
CMD ["java", "-jar", "app.jar"]