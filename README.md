# EcoWarm - Controlador Domótico (Iteración 5)

Sistema de control inteligente de calefacción basado en IoT, desarrollado por el **Grupo 3**. Este proyecto implementa una arquitectura modular separando la lógica de negocio pura (`core`), la infraestructura y comunicaciones (`engine`) y un servidor **Model Context Protocol (`mcp`)** para integración con agentes de IA, garantizando resiliencia, alta tolerancia a fallos y un estricto control de calidad.

## 🚀 Requisitos Previos

Para ejecutar la infraestructura local del proyecto, asegúrese de contar con:

* **Docker y Docker Compose** (Sintaxis moderna `docker compose` sin guion).

* **Git Bash** o terminal compatible con scripts `.sh` (Para usuarios de Windows).

* **Java 25** y **Maven** (Solo requerido para desarrollo local y ejecución de tests manuales, el despliegue está completamente dockerizado).

## 🔐 Configuración de Seguridad (.env)

A partir de la Iteración 5, todas las credenciales y URLs han sido removidas del código fuente. Antes de levantar la infraestructura, es **obligatorio** crear un archivo `.env` dentro de la carpeta `docker/` con las siguientes variables:

```
DB_USER
DB_PASS
DB_NAME
DB_URL
MQTT_BROKER_URL
SIMULADOR_URL
APP_API_KEY
SIMULADOR_API_KEY

```

## 🏗️ Despliegue de la Infraestructura

El proyecto incluye scripts automatizados en la carpeta `/scripts` que se encargan de aprovisionar la base de datos PostgreSQL, el motor (`:8080`), el servidor MCP (`:8081`) y las redes, limpiando siempre el estado anterior para evitar conflictos.

1. **Levantar el sistema desde cero:**
   Abra su terminal en la raíz del proyecto y ejecute:

   ```
   bash scripts/up.sh
   
   ```

   *Este comando construirá las imágenes del motor y del servidor MCP y levantará todos los contenedores.*

2. **Detener y limpiar la infraestructura:**
   Para apagar el sistema y **purgar los volúmenes** de la base de datos (garantizando un reinicio limpio en la próxima ejecución):

   ```
   bash scripts/down.sh
   
   ```

## ⏱️ Soporte de Tiempo Acelerado (Sprint 5)

El motor está diseñado para soportar el mecanismo de tiempo acelerado provisto por la nueva versión del simulador. Al inicio del ciclo, el controlador lee dinámicamente el **factor de aceleración** enviado por la API del simulador y ajusta su reloj interno.
Esto permite que el sistema evalúe las reglas de negocio y los horarios punta (ej. 17:00 a 23:00) sincronizado a la velocidad de la simulación (ej. 60x), independientemente del tiempo real del servidor.

## 🛡️ Resiliencia y Tolerancia a Fallos

El sistema implementa un patrón **Fail-Safe** riguroso:

* Si la comunicación REST con el simulador falla o un termostato deja de emitir datos MQTT, el error es aislado.

* El controlador detiene la calefacción de la habitación afectada por seguridad, pero **el ciclo general del motor continúa operando** para el resto de la casa sin colapsar.

## 🤖 Servidor MCP para Agentes de IA (`modules/mcp`)

El proyecto incluye un servidor **Model Context Protocol (MCP)** sobre HTTP + SSE en el puerto `8081` (`/mcp/sse` y `/mcp/message`), desarrollado con **Spring AI 1.0**. Expone **7 tools** (`obtenerSitio`, `consultarEstadoHabitaciones`, `historialTemperaturas`, `obtenerEstadoControl`, `iniciarControl`, `detenerControl` y `accionarSwitch`) que combinan la API REST del `engine`, los switches del simulador y lectura en modo `read-only` de PostgreSQL.

* **Arquitectura 4+1 del Módulo MCP:** [`/docs/mcp/4mas1-mcp.md`](docs/mcp/4mas1-mcp.md)
* **Evidencia de Pruebas E2E con MCP Inspector y Simulador:** [`/docs/mcp/pruebas-mcp.md`](docs/mcp/pruebas-mcp.md)

## 🧪 Calidad y Testing

* **Tests Unitarios y de Integración:** El proyecto cuenta con **43 tests automatizados** distribuidos en `EvaluadorCoreTest` (13 tests en `core`), `EngineIntegrationTest` (8 tests en `engine`) y la suite del servidor MCP (`EcoWarmToolsTest`, `ClientesHttpTest` y `McpServerContextTest`, con 22 tests en `mcp`).

* **Cobertura (JaCoCo):** El módulo `core` posee un nivel de cobertura de pruebas superior al **85%**. El reporte HTML interactivo se encuentra disponible en `/docs/calidad/cobertura/index.html`.

* **Estándares de Codificación:** Las reglas de nomenclatura, formato y buenas prácticas adoptadas por el equipo están documentadas en `/docs/calidad/estandares_de_codificacion.md`.

## 📂 Estructura del Proyecto

* `/docs/`: Documentación de arquitectura (Vistas 4+1), documentación y pruebas del servidor MCP (`/docs/mcp/`) y evidencias de calidad.

* `/docker/`: Archivos de configuración de contenedores, Dockerfile y `.env`.

* `/modules/core/`: Lógica de negocio, reglas de dominio y tests unitarios.

* `/modules/engine/`: Ciclo de control, integración MQTT/REST, resiliencia y tests de integración.

* `/modules/mcp/`: Servidor MCP (Spring AI) con 7 tools para agentes de IA y su suite de pruebas.

* `/scripts/`: Automatización de despliegue (`up.sh`, `down.sh`, `build.sh`, `tiempo-acelerado`).
