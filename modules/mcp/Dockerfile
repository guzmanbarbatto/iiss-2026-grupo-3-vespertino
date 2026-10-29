# Etapa 1: Compilación (Build Stage)
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /app

# Copiamos todo el repositorio para que Maven detecte el POM padre
COPY . .

# Compilamos exclusivamente el módulo 'mcp'
RUN mvn clean package -pl modules/mcp -am

# Etapa 2: Ejecución (Run Stage)
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

COPY --from=builder /app/modules/mcp/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
