# EcoWarm - Controlador Domótico (Iteración 5)

Sistema de control inteligente de calefacción basado en IoT, desarrollado por el **Grupo 3**. Este proyecto implementa una arquitectura modular separando la lógica de negocio pura (`core`), la infraestructura y comunicaciones (`engine`) y un servidor **Model Context Protocol (`mcp`)** para integración con agentes de IA, garantizando resiliencia, alta tolerancia a fallos y un estricto control de calidad.

## 🚀 Requisitos Previos

Para ejecutar la infraestructura local del proyecto, asegúrese de contar con:

* **Docker y Docker Compose** (Sintaxis moderna `docker compose` sin guion).

* **Git Bash** o terminal compatible con scripts `.sh` (Para usuarios de Windows).

* **Java 25** y **Maven** (Solo requerido para desarrollo local y ejecución de tests manuales, el despliegue está completamente dockerizado).

## 🔐 Configuración de Seguridad (.env)

A partir de la Iteración 5, todas las credenciales y URLs han sido removidas del código fuente. Antes de levantar la infraestructura, es **obligatorio** crear un archivo `.env` dentro de la carpeta `docker/` (está en `.gitignore`: nunca se sube al repositorio) con las siguientes variables:

| Variable | Obligatoria | Uso |
|---|---|---|
| `DB_USER`, `DB_PASS`, `DB_NAME` | Sí | Credenciales y nombre de la base PostgreSQL. |
| `DB_URL` | Sí | JDBC del engine y del MCP, por ejemplo `jdbc:postgresql://postgres-db:5432/ecowarm`. |
| `APP_API_KEY` | Sí | Clave que exige el engine en `X-API-Key` y que declara al registrarse en el simulador. |
| `SIMULADOR_URL` | Sí | Base del simulador (registro y switches), por ejemplo `http://host.docker.internal:9090`. |
| `SIMULADOR_API_KEY` | No | Clave del simulador. Vacía si el simulador no exige credencial. |
| `CONTROLADOR_BASE_URL` | No | Dirección en la que el simulador alcanza al engine (se declara en el registro). Por defecto `http://localhost:8080`. |
| `MQTT_BROKER_URL` | No | Broker MQTT. Vacía: se usa el que anuncia el simulador al registrarse (§6). Definida, tiene prioridad (útil si el nombre anunciado no es alcanzable desde esta red). |
| `MCP_API_KEY` | No | Si se define, el servidor MCP exige ese `X-API-Key`. Sin ella el MCP queda abierto: definila fuera de desarrollo local. |
| `MCP_CORS_ORIGINS` | No | Orígenes web que pueden usar el MCP desde un navegador. Por defecto solo `localhost`. |

## 🏗️ Despliegue de la Infraestructura

El proyecto incluye scripts automatizados en la carpeta `/scripts` que se encargan de aprovisionar la base de datos PostgreSQL, el motor (`:8080`), el servidor MCP (`:8081`) y las redes. El engine crea (o actualiza) el esquema de la base al arrancar.

1. **Levantar el sistema desde cero:**
   Abra su terminal en la raíz del proyecto y ejecute:

   ```
   bash scripts/up.sh
   
   ```

   *Este comando construirá las imágenes del motor y del servidor MCP y levantará todos los contenedores.*

2. **Detener sin perder datos:** `bash scripts/stop.sh` (se vuelve a levantar con `up.sh`).

3. **Detener y limpiar la infraestructura:**
   Para apagar el sistema y **purgar los volúmenes** de la base de datos de este proyecto (garantizando un reinicio limpio en la próxima ejecución):

   ```
   bash scripts/down.sh
   
   ```

## ⏱️ Soporte de Tiempo Acelerado (Sprint 5)

El motor soporta el tiempo acelerado del contrato. El `POST /control/comandos` con `accion: INICIAR` trae la `fechaHora` a la que se sincroniza el reloj y el **factor de aceleración** (`factorTiempo`, por defecto 1). Desde ese momento el controlador usa `ahora = fechaHora + tiempoReal × factorTiempo` para todo lo que depende de la hora, y evalúa cada 30 segundos de ese tiempo lógico.
Esto permite evaluar las reglas de negocio y los horarios punta (ej. 17:00 a 23:00) a la velocidad de la simulación (ej. 60x), independientemente del tiempo real del servidor. Para probarlo a mano: `bash scripts/tiempo-acelerado.sh [fechaHora] [factor]` (toma la clave de `docker/.env`).

## 🏠 Demo con 10 habitaciones

El simulador de la cátedra tiene el sitio fijo con 2 habitaciones. Para ver cómo el engine reparte la potencia entre muchas, `demo/` incluye un simulador de prueba con **10 habitaciones (10,1 kW en total) y 3,7 kW contratados**. Usa contenedores, puertos y base propios: no toca el stack real ni sus datos.

```
bash scripts/build.sh                        # una vez, si las imágenes no están construidas
bash scripts/demo-10-habitaciones.sh         # levanta la demo y muestra el log resumido (Ctrl+C la termina y la borra)
bash scripts/demo-10-habitaciones.sh tablero # en otra ventana: temperatura y estado de cada habitación en vivo
```

Por defecto arranca el martes 27/10 a las 13:00 simuladas a ×60: se ven los turnos entre habitaciones y, unos 4 minutos reales después, la entrada de la tarifa punta (17:00). Se puede elegir otra hora y otro factor: `bash scripts/demo-10-habitaciones.sh 2026-10-27T08:00:00-03:00 30`.

## 🛡️ Resiliencia y Tolerancia a Fallos

El sistema implementa un patrón **Fail-Safe** riguroso:

* Si un termostato deja de emitir datos MQTT (sin lecturas en los últimos 10 minutos lógicos), el controlador apaga la calefacción de esa habitación por seguridad, pero **el ciclo general del motor continúa operando** para el resto de la casa.

* El estado de cada switch se consulta al simulador en cada ciclo: un cambio hecho desde afuera (MCP, consola del simulador) se detecta y se corrige. Un switch que no responde o está `DESCONOCIDO` se comanda explícitamente, sin suponer su estado.

* Si se corta el broker MQTT, el engine se reconecta y **vuelve a suscribirse** solo. Si el simulador se reinicia y olvida el registro, el engine se registra de nuevo (lo verifica cada minuto).

* Una lectura que oscila alrededor del objetivo no hace titilar el relé: se apaga recién al superar la temperatura esperada por 0,5 °C (histéresis).

## 🤖 Servidor MCP para Agentes de IA (`modules/mcp`)

El proyecto incluye un servidor **Model Context Protocol (MCP)** sobre HTTP + SSE en el puerto `8081` (`/mcp/sse` y `/mcp/message`), desarrollado con **Spring AI 1.0**. Expone **7 tools** (`obtenerSitio`, `consultarEstadoHabitaciones`, `historialTemperaturas`, `obtenerEstadoControl`, `iniciarControl`, `detenerControl` y `accionarSwitch`) que combinan la API REST del `engine`, los switches del simulador y lectura en modo `read-only` de PostgreSQL.

* **Arquitectura 4+1 del Módulo MCP:** [`/docs/mcp/4mas1-mcp.md`](docs/mcp/4mas1-mcp.md)
* **Evidencia de Pruebas E2E con MCP Inspector y Simulador:** [`/docs/mcp/pruebas-mcp.md`](docs/mcp/pruebas-mcp.md)

## 🧪 Calidad y Testing

* **Tests Unitarios y de Integración:** El proyecto cuenta con **133 tests automatizados** (`mvn verify`): 23 en `core` (`EvaluadorCoreTest`), 83 en `engine` (ciclo de control, API REST contra el contrato, validación del inventario, MQTT, registro y repositorios contra PostgreSQL real) y 27 en `mcp`. Los tests de repositorios usan Testcontainers: necesitan Docker y, sin él (por ejemplo dentro de `docker build`), se omiten. El detalle está en [`/docs/tests/criterio-tests.md`](docs/tests/criterio-tests.md).

* **Cobertura (JaCoCo):** El módulo `core` posee un nivel de cobertura de pruebas superior al **85%**. El reporte HTML interactivo se encuentra disponible en `/docs/calidad/cobertura/index.html`.

* **Estándares de Codificación:** Las reglas de nomenclatura, formato y buenas prácticas adoptadas por el equipo están documentadas en `/docs/calidad/estandares_de_codificacion.md`. Checkstyle las verifica en los tres módulos con `google_checks.xml` y el build falla ante cualquier violación.

## 📂 Estructura del Proyecto

* `/docs/`: Documentación de arquitectura (Vistas 4+1), documentación y pruebas del servidor MCP (`/docs/mcp/`) y evidencias de calidad.

* `/docker/`: `docker-compose.yml` y el `.env` local (no versionado). Cada módulo tiene su `Dockerfile`.

* `/modules/core/`: Lógica de negocio, reglas de dominio y tests unitarios.

* `/modules/engine/`: Ciclo de control, integración MQTT/REST, resiliencia y tests de integración.

* `/modules/mcp/`: Servidor MCP (Spring AI) con 7 tools para agentes de IA y su suite de pruebas.

* `/scripts/`: Automatización de despliegue (`up.sh`, `stop.sh`, `down.sh`, `build.sh`, `tiempo-acelerado.sh`, `demo-10-habitaciones.sh`).

* `/demo/`: Simulador de prueba con 10 habitaciones y su configuración de Docker.
