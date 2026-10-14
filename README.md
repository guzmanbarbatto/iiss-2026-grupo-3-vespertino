# EcoWarm - Sistema de Domótica Inteligente 🌡️ (Iteración 4)

EcoWarm es un sistema backend de control domótico diseñado para gestionar la calefacción de hogares, optimizando el consumo energético en base a la temperatura ambiente y las restricciones de la Tarifa Punta eléctrica.

Esta versión (Iteración 4) implementa una **Arquitectura Multi-Módulo (Hexagonal)**, separa el dominio puro (TDD) de la infraestructura, y cumple con el contrato OpenAPI v2.0 establecido por la cátedra para la interoperabilidad.

## 🏗️ Arquitectura Multi-Módulo

El proyecto está dividido en dos módulos Maven principales:
* **`core`**: Cerebro matemático del sistema. Escrito en Java puro, sin frameworks. Contiene el modelo de dominio y la evaluación de reglas tarifarias.
* **`engine`**: Adaptador de infraestructura (Spring Boot). Expone la API REST segura, se suscribe a sensores vía MQTT (Eclipse Paho) y persiste el estado en PostgreSQL.

## 🚀 Requisitos Previos

* **Docker y Docker Compose** instalados (para la infraestructura de BD y Broker).
* **Simulador Oficial de la Cátedra** (provisto externamente).
* Java 25 y Maven 3.9 (opcional si se utiliza el script de construcción por Docker).

## 🛠️ Cómo compilar y correr el sistema

Todo el ciclo de vida de la aplicación está automatizado en la carpeta `/scripts`.

1. **Construir el sistema y correr tests:**
   Desde la raíz del proyecto, ejecuta:
   ```bash
   ./scripts/build.sh
   ```
   *Nota: Este paso descarga las dependencias, ejecuta los tests unitarios del `core` y construye la imagen Docker del `engine`.*

2. **Levantar la infraestructura:**
   ```bash
   ./scripts/up.sh
   ```
   Esto levantará en segundo plano:
   * **PostgreSQL:** Puerto `5432`
   * **Mosquitto (MQTT):** Puerto `1883`
   * **Engine (API REST):** Puerto `8080`

3. **Apagar la infraestructura:**
   ```bash
   ./scripts/down.sh
   ```

## 🔌 Integración con el Simulador (Iteración 4)

El sistema está configurado para integrarse automáticamente con el simulador externo proporcionado.

### Pasos de integración:
1. Asegúrate de levantar el Simulador en tu máquina host antes (o inmediatamente después) de levantar EcoWarm. El simulador debe escuchar en el puerto **`9090`**.
2. EcoWarm utilizará la URL `http://host.docker.internal:9090` para comunicarse con el simulador desde adentro de su contenedor Docker.
3. El `Engine` enviará automáticamente los comandos `ON`/`OFF` a los relés mediante peticiones seguras utilizando el token definido en la variable de entorno `SIMULADOR_API_KEY`.

### Seguridad (API Keys)
Todas las peticiones a la API REST de nuestro `engine` (puerto `8080`) están protegidas. Para interactuar con nuestro controlador, se debe enviar el siguiente Header HTTP:
* **Header:** `X-API-Key`
* **Valor por defecto:** `clave-secreta-grupo-3` (Configurable en el `docker-compose.yml`).

## 🧪 Pruebas Unitarias (TDD)

Las reglas de negocio estrictas de encendido y apagado están cubiertas al 100% por pruebas unitarias de caja negra, validando límites de tiempo y potencia.

Para correr las pruebas manualmente sin levantar contenedores, ejecuta:
```bash
mvn test -pl modules/core
```
Ver detalles del diseño de pruebas en: `/docs/tests/criterio-tests.md`.