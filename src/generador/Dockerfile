# Etapa 1: Aprovisionamiento de dependencias y compilación (Build Stage)
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /app

# Inyección del modelo de dependencias y el código fuente
COPY pom.xml .
COPY src ./src

# Compilación aislada y empaquetado del artefacto binario (Fat JAR)
RUN mvn clean package -DskipTests

# Etapa 2: Configuración del entorno de ejecución (Runtime Stage)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Extracción estricta del binario ensamblado con sus dependencias (Fat JAR)
COPY --from=builder /app/target/*-jar-with-dependencies.jar app.jar

# Invocación del proceso de telemetría bajo la Máquina Virtual de Java (JVM)
ENTRYPOINT ["java", "-jar", "app.jar"]