# EcoWarm: controlador de calefacción

EcoWarm es el controlador de calefacción del **Grupo 3** (IISS 2026, UTEC). Cada 30 segundos de tiempo simulado decide qué habitaciones calentar según su temperatura, la potencia contratada y la tarifa punta, y comanda los calefactores con los dispositivos del estándar `interop-v2`: termostatos que publican por MQTT y switches que se manejan por HTTP. En el curso, esos dispositivos los provee el simulador de la cátedra.

> **Estado al traspaso (Iteración 6, noviembre de 2026).** El controlador pasa los 9 escenarios de `ecowarm-run` sin ninguna expectativa en rojo, y fue el único de los cuatro grupos que pasó los tres escenarios que corrió el profesor el 4/11. Tiene 220 tests y el CI los corre en cada pull request. Lo que falta y las limitaciones conocidas están en [`docs/arquitectura/roadmap.md`](docs/arquitectura/roadmap.md).
>
> **Si van a tomar el proyecto, empiecen por la [guía para el equipo receptor](docs/guia-equipo-receptor.md):** qué pedir, un plan para la primera semana, un recorrido por el código y qué cuidar al cambiarlo.

## Contenido

1. [Qué se entrega](#1-qué-se-entrega)
2. [Puesta en marcha](#2-puesta-en-marcha)
3. [Configuración](#3-configuración)
4. [Operación](#4-operación)
5. [Problemas conocidos y cómo resolverlos](#5-problemas-conocidos-y-cómo-resolverlos)
6. [Desarrollo](#6-desarrollo)
7. [Mapa de la documentación](#7-mapa-de-la-documentación)
8. [Cómo se comporta](#8-cómo-se-comporta)

## 1. Qué se entrega

| Pieza | Dónde | Estado |
|---|---|---|
| Código: reglas de control (`core`), controlador (`engine`) y servidor MCP para agentes de IA (`mcp`) | [`modules/`](modules/) | Java 25 y Spring Boot 3.5. 220 tests en verde |
| Despliegue con Docker Compose y scripts | [`docker/`](docker/) y [`scripts/`](scripts/) | Probado en Windows con Docker Desktop. En Linux hace falta un ajuste (ver [problemas conocidos](#5-problemas-conocidos-y-cómo-resolverlos)) |
| Integración continua | [`.github/workflows/maven.yml`](.github/workflows/maven.yml) | Corre en cada pull request y en cada push a `main` |
| Documentación de arquitectura, contratos, tests, calidad y producto | [`docs/`](docs/) | Ver el [mapa de la documentación](#7-mapa-de-la-documentación) |
| Versiones | Tags de git `version1` a `version5`; `version6` al cerrar esta iteración | Una por iteración |

### Lo que no está en el repositorio

| Qué | Para qué sirve | Cómo conseguirlo |
|---|---|---|
| `docker/.env`, con las claves reales | Levantar el sistema | Armarlo a partir de [`docker/.env.example`](docker/.env.example) con **claves nuevas**: una clave vieja quedó en el historial de git |
| `ecowarm-run.jar` (y el anterior, `ecowarm-check.jar`) | El simulador de la cátedra y sus escenarios firmados | Los provee la cátedra. No se versionan |
| El tablero de Jira | Historias, backlog y estado de las tareas ([metodología](docs/metodologia.md)) | Pedir acceso o una exportación |
| El repositorio en GitHub y sus Actions | Código, pull requests y CI | `github.com/guzmanbarbatto/iiss-2026-grupo-3-vespertino`: pedir acceso de escritura al dueño |

## 2. Puesta en marcha

**Requisitos:**
- **Docker Desktop**, con `docker compose` (v2).
- **Git Bash** u otra terminal que corra scripts `.sh`, si se trabaja en Windows.
- **Java 25 y Maven 3.9**, solo para compilar y correr los tests fuera de Docker.
- **Java 21 o más**, para correr `ecowarm-run`.

1. **Configurar:** copiar la plantilla y completar las claves (ver [Configuración](#3-configuración)):
   ```bash
   cp docker/.env.example docker/.env
   ```
2. **Levantar** PostgreSQL, el engine (`:8080`) y el MCP (`:8081`). El engine crea o actualiza el esquema de la base al arrancar:
   ```bash
   bash scripts/up.sh
   ```
3. **Ver el log del engine.** Tiene que decir `Registrado en el simulador` cuando el simulador esté corriendo:
   ```bash
   docker logs -f ecowarm_engine
   ```
4. **Correr los escenarios de la cátedra,** desde la carpeta donde esté `ecowarm-run.jar` (tarda unos 18 minutos):
   ```bash
   CLAVE=$(grep '^SIMULADOR_API_KEY=' <ruta al repo>/docker/.env | cut -d= -f2- | tr -d '\r')
   java -jar ecowarm-run.jar --host host.docker.internal --clave "$CLAVE" todos
   ```
   `--host host.docker.internal` hace que los `urlSwitch` y el broker que recibe el engine (en Docker) apunten a la máquina, donde corre el runner. El resultado queda en `ecowarm-reporte/reporte.md`, y `java -jar ecowarm-run.jar listar` muestra los escenarios. La consola del runner muestra la clave: para compartir un resultado, usar el `reporte.md`.
5. **Correr los tests,** con Docker encendido:
   ```bash
   mvn clean verify
   ```

## 3. Configuración

Todo se configura con variables de entorno en `docker/.env`. La plantilla [`docker/.env.example`](docker/.env.example) tiene todas, comentadas.

| Variable | Obligatoria | Uso |
|---|---|---|
| `DB_USER`, `DB_PASS`, `DB_NAME` | Sí | Credenciales y nombre de la base PostgreSQL. |
| `DB_URL` | Sí | JDBC del engine y del MCP, por ejemplo `jdbc:postgresql://postgres-db:5432/ecowarm`. |
| `APP_API_KEY` | Sí | Clave que exige el engine en `X-API-Key` y que le declara al simulador al registrarse. |
| `SIMULADOR_URL` | Sí | Base del simulador para el registro (§6), por ejemplo `http://host.docker.internal:9090`. |
| `SIMULADOR_API_KEY` | No | Clave del simulador. `ecowarm-run` usa `iiss-2026` si no se le pasa `--clave`. |
| `CONTROLADOR_BASE_URL` | No | Dirección en la que el simulador alcanza al engine; se declara en el registro. Por defecto, `http://localhost:8080`. **Si no se usa, comentarla:** definida pero vacía, el engine se registra con una dirección vacía. |
| `MQTT_BROKER_URL` | No | Broker MQTT. Si no se define, se usa el que anuncia el simulador al registrarse. |
| `SWITCHES_URL` | No | Reemplaza el `urlSwitch` de todas las habitaciones, en el engine y en el MCP, cuando el que anuncia el simulador no es alcanzable desde esta red. |
| `MCP_API_KEY` | No | Si se define, el servidor MCP exige ese `X-API-Key`. Sin ella el MCP queda abierto: definirla fuera de desarrollo local. |
| `MCP_CORS_ORIGINS` | No | Orígenes web que pueden usar el MCP desde un navegador. Por defecto, solo `localhost`. |

**Simulador en otra máquina de la red local** (como en la clase del 4/11): `SIMULADOR_URL` y `MQTT_BROKER_URL` con la IP y los puertos de esa máquina, y `CONTROLADOR_BASE_URL=http://<IP de esta máquina>:8080`. Las dos máquinas tienen que estar en la misma red. El detalle de cada forma de desplegarlo está en la [Vista Física](docs/arquitectura/4mas1.md#4-vista-física).

## 4. Operación

| Script | Qué hace |
|---|---|
| `bash scripts/up.sh` | Construye las imágenes y levanta PostgreSQL, engine y MCP |
| `bash scripts/stop.sh` | Detiene los contenedores sin borrar nada |
| `bash scripts/down.sh` | Borra contenedores, redes y volúmenes, **la base incluida** |
| `bash scripts/build.sh` | Solo construye las imágenes |
| `bash scripts/tiempo-acelerado.sh [fechaHora] [factor]` | Arranca el control a mano en una fecha y con un factor de aceleración (toma la clave de `docker/.env`) |
| `bash scripts/demo-10-habitaciones.sh` | Demo con un simulador propio de 10 habitaciones (ver abajo) |

**El log del engine** (`docker logs -f ecowarm_engine`) tiene una línea por ciclo con cambios, con la hora simulada y el reparto de potencia:
```
[simulado mar 03/11 10:00] room2 → ON, room4 → ON | en uso 1.9 de 2.0 kW: room2, room4
```
Cada falla (simulador, base, broker, un switch o un termostato) se avisa una vez cuando empieza y otra cuando se recupera.

**El control arranca con el `INICIAR` del simulador** (`POST /control/comandos`), que trae la `fechaHora` y el `factorTiempo`. Si el engine se reinicia, el sitio se conserva, pero el control queda detenido hasta el próximo `INICIAR`.

**Demo con 10 habitaciones.** `demo/` tiene un simulador propio con 10 habitaciones (10,1 kW en total) y 3,7 kW contratados, para ver el reparto de potencia sin el simulador de la cátedra. Usa contenedores, puertos y base propios: no toca el stack real.
```bash
bash scripts/build.sh                        # una vez, si las imágenes no están construidas
bash scripts/demo-10-habitaciones.sh         # levanta la demo (Ctrl+C la termina y la borra)
bash scripts/demo-10-habitaciones.sh tablero # en otra ventana: cada habitación en vivo
```
Arranca el martes 27/10 a las 13:00 simuladas a ×60; unos 4 minutos reales después entra la tarifa punta. Se puede elegir otra hora y otro factor: `bash scripts/demo-10-habitaciones.sh 2026-10-27T08:00:00-03:00 30`.

**Servidor MCP para agentes de IA** (`modules/mcp`). Es un servidor MCP sobre HTTP con SSE en el puerto 8081 (`/mcp/sse` y `/mcp/message`), hecho con Spring AI. Tiene 7 tools: `obtenerSitio`, `consultarEstadoHabitaciones`, `historialTemperaturas`, `obtenerEstadoControl`, `iniciarControl`, `detenerControl` y `accionarSwitch`. Su arquitectura está en [`docs/mcp/4mas1-mcp.md`](docs/mcp/4mas1-mcp.md) y sus pruebas, en [`docs/mcp/pruebas-mcp.md`](docs/mcp/pruebas-mcp.md).

## 5. Problemas conocidos y cómo resolverlos

Los que encontramos durante el desarrollo y las pruebas con la cátedra:

| Síntoma | Causa | Solución |
|---|---|---|
| El log dice `El simulador no responde en …/controladores (401 Unauthorized …)` | `SIMULADOR_API_KEY` no es la clave del simulador | Poner la del simulador (`ecowarm-run` usa `iiss-2026` por defecto) y reiniciar con `bash scripts/up.sh` |
| El engine se registra, pero un simulador en otra máquina nunca le manda el sitio | Se registró como `http://localhost:8080`, y para el simulador `localhost` es su propia máquina | `CONTROLADOR_BASE_URL=http://<IP de esta máquina>:8080` |
| `ecowarm-run` espera el registro y no avanza | El engine no está levantado, o `SIMULADOR_URL` no apunta a la máquina del runner | Ver `docker logs ecowarm_engine`. Con el runner en la misma máquina: `SIMULADOR_URL=http://host.docker.internal:9090` |
| En Linux, nada llega a `host.docker.internal` | Docker Engine en Linux no lo define; Docker Desktop sí | Usar la IP de la máquina, o agregar `extra_hosts: ["host.docker.internal:host-gateway"]` al engine y al MCP en `docker/docker-compose.yml` |
| `ecowarm-run` rechaza la clave aunque es la correcta | `docker/.env` tiene finales de línea de Windows y la clave se lleva un `\r` | Extraerla con `tr -d '\r'`, como en la [puesta en marcha](#2-puesta-en-marcha) |
| `Connect timed out` hacia los switches de otra máquina | La red (por ejemplo, Wi-Fi) | El engine reintenta en cada ciclo y nunca se pasa de la contratada. Si es constante, revisar que esa IP y ese puerto respondan desde esta máquina |
| `docker compose build` falla con `Premature end of Content-Length` | Una descarga de Maven Central se cortó | Volver a correr `bash scripts/up.sh` |
| Puerto ocupado al levantar el stack o el runner | Otro programa usa el 5432, 8080, 8081, 9090 o 1883 | Detenerlo, o detener una corrida anterior del runner |
| `mvn verify` pasa, pero con tests omitidos | Docker no está corriendo: los tests con Testcontainers se omiten en silencio | Encender Docker. El CI falla si se omite algún test |
| Después de reiniciar el engine, nada se enciende | El control queda detenido hasta el próximo `INICIAR` | Que el simulador mande `INICIAR`, o `bash scripts/tiempo-acelerado.sh` |

## 6. Desarrollo

- **Build y calidad:** `mvn clean verify` desde la raíz compila los tres módulos, corre los tests y los controles. Checkstyle aplica [`google_checks.xml`](google_checks.xml) (líneas de hasta 120 columnas, también en los tests), y una sola violación corta el build. JaCoCo exige 85 % de cobertura en `core`; el reporte está en [`docs/calidad/cobertura/index.html`](docs/calidad/cobertura/index.html). Las convenciones del equipo están en [`docs/calidad/estandares_de_codificacion.md`](docs/calidad/estandares_de_codificacion.md).
- **Tests:** 220 (37 en `core`, 151 en `engine` y 32 en `mcp`), entre ellos un test de punta a punta con 14 escenarios, contra PostgreSQL y Mosquitto reales (Testcontainers). El criterio de cada uno está en [`docs/tests/criterio-tests.md`](docs/tests/criterio-tests.md).
- **Validación con la cátedra:** `ecowarm-run` (paso 4 de la [puesta en marcha](#2-puesta-en-marcha)) antes de entregar.
- **Cómo trabajamos:**
  - Una rama por cambio desde `main`, con nombre `Sprint<N>-<Tema>`.
  - Un pull request por rama, con una descripción de qué cambia, por qué y cómo se verificó.
  - El CI tiene que pasar (`tests` y `build-and-verify`) y se mergea con merge commit.
  - Cada arreglo lleva un test que falla si se deshace el arreglo.
  - Al cerrar cada iteración, un tag `version<N>`.
  - Las tareas se gestionan en Jira con Scrumban ([metodología](docs/metodologia.md)).

```text
├── modules/core/      # reglas de control, Java puro (sin dependencias)
├── modules/engine/    # el controlador: API REST, MQTT, switches, ciclo de control y PostgreSQL
├── modules/mcp/       # servidor MCP para agentes de IA (Spring AI)
├── docker/            # docker-compose.yml, .env.example y .env (este último no se versiona)
├── scripts/           # levantar, detener, construir y demos
├── demo/              # simulador de 10 habitaciones para la demo
└── docs/              # arquitectura, contratos, tests, calidad y producto
```

## 7. Mapa de la documentación

| Documento | Qué tiene | Cuándo leerlo |
|---|---|---|
| [`docs/guia-equipo-receptor.md`](docs/guia-equipo-receptor.md) | Qué pedir, plan de la primera semana, recorrido por el código, cómo hacer un cambio, qué cuidar y cómo validar contra la cátedra | Antes que todo, si toman el proyecto |
| [`docs/arquitectura/4mas1.md`](docs/arquitectura/4mas1.md) | La arquitectura en 5 vistas: lógica, desarrollo, procesos, física y escenarios | Primero |
| [`docs/arquitectura/decisiones.md`](docs/arquitectura/decisiones.md) | 20 decisiones con su motivo y las alternativas descartadas, y las que se revirtieron | Antes de cambiar un comportamiento |
| [`docs/arquitectura/roadmap.md`](docs/arquitectura/roadmap.md) | Lo validado, las limitaciones conocidas y las prioridades | Para planificar |
| [`docs/api/`](docs/api/) | Los contratos OpenAPI del controlador y del simulador (`interop-v2`) | Ante cualquier duda de integración |
| [`docs/tests/criterio-tests.md`](docs/tests/criterio-tests.md) | Qué prueba cada test y por qué | Antes de tocar un test |
| [`docs/mcp/`](docs/mcp/) | La arquitectura y las pruebas del servidor MCP | Para trabajar en el MCP |
| [`docs/calidad/`](docs/calidad/) | Estándares de codificación y reporte de cobertura | Al empezar a programar |
| [`docs/vision.md`](docs/vision.md), [`docs/producto/`](docs/producto/) | Visión del producto, características, historias de usuario y escenarios | Para entender el negocio |
| [`docs/metodologia.md`](docs/metodologia.md) | Scrumban y el tablero de Jira | Para organizar el trabajo |
| [`docs/arquitectura/diagramas/`](docs/arquitectura/diagramas/) | Las fuentes de los diagramas (Mermaid) y cómo regenerarlos | Al cambiar un diagrama |

## 8. Cómo se comporta

Lo esencial; el detalle y el porqué de cada punto están en el [4+1](docs/arquitectura/4mas1.md) y en las [decisiones](docs/arquitectura/decisiones.md).

* **Reparto de potencia.** Nunca se pasa de la potencia contratada. Con potencia escasa, enciende la combinación de habitaciones que más la aprovecha; entre combinaciones iguales, la que incluye a las más lejos de su objetivo. Si la contratada baja, apaga lo que sobra.
* **Tarifa punta.** No queda nada encendido dentro de la punta: si empieza antes de los próximos dos ciclos, apaga ya.
* **Histéresis.** Enciende apenas una habitación baja de su temperatura esperada y la apaga al superarla por 0,5 °C, así el relé no titila.
* **Fail-safe.** Sin lecturas válidas de un termostato, su habitación se apaga y el resto de la casa sigue. Se considera sin lecturas cuando pasan 10 minutos simulados o 3 lecturas perdidas, lo que sea mayor. Un mensaje fuera del contrato (no es JSON, no trae `tC`, la trae como texto o fuera de −10 a 50 °C) no cuenta como lectura.
* **Switches.** Cada switch se consulta y se comanda en el `urlSwitch` de su habitación. Su estado real se relee cada 5 s como máximo, y nunca se lo supone: un estado desconocido se comanda explícitamente.
* **Switches que fallan.** Todos se llaman a la vez, con 3 s para conectar y 1 s para la respuesta, así que un switch colgado no frena a los demás. Lo que no se pudo apagar cuenta como encendido.
* **Detener.** Con el control detenido no se comanda nada, aunque el `DETENER` llegue en medio de un ciclo. Solo se comandan los switches del sitio vigente.
* **MQTT y registro.** El engine se suscribe exactamente a los topics del inventario y vuelve a suscribirse solo si se corta el broker. Se vuelve a registrar si el simulador se reinicia.
