# Criterios de Adecuación del Conjunto de Tests

Este documento detalla el diseño y los criterios de los tests del proyecto: las reglas del `core` (sección 2), el `engine` (sección 3), el test de punta a punta (sección 4), la integración continua (sección 5) y la evidencia final (sección 6). Los tests del servidor MCP están en [`docs/mcp/pruebas-mcp.md`](../mcp/pruebas-mcp.md).

## 1. Estrategia y Técnicas Aplicadas
El conjunto de tests fue diseñado utilizando técnicas de **Pruebas de Caja Negra**, enfocándose en los requisitos funcionales del controlador: las reglas de control y el estándar `interop-v2`. Las técnicas principales utilizadas fueron:
*   **Partición de Equivalencia:** Para agrupar entradas válidas (ej. días hábiles vs. fines de semana, temperaturas altas vs. bajas).
*   **Análisis de Valores Límite:** Para probar el comportamiento del sistema exactamente en los bordes horarios de la tarifa punta y en los umbrales de potencia contratada.

## 2. Cobertura de Reglas de Negocio

### A. Regla 1: Tarifa Punta (Restricción Económica)
Se verificó que el sistema priorice el ahorro energético apagando todos los dispositivos durante la tarifa punta, sin importar la temperatura actual.
*   **Particiones comunes:** Se crearon tests para verificar el comportamiento dentro de la franja horaria (lunes a viernes, 17:00 a 23:00) forzando el apagado de equipos encendidos. También se evaluaron días de fin de semana para garantizar que la restricción no aplique.
*   **Casos Borde (Valores Límite):** 
    *   *Límite Inferior Inclusivo:* Se evaluó el sistema exactamente a las `17:00:00`. El test garantiza que el corte se activa de forma inmediata en el segundo inicial.
    *   *Límite Superior Exclusivo:* Se evaluó el sistema exactamente a las `23:00:00`. El test verifica que la restricción se levanta, permitiendo encender equipos nuevamente.

### B. Control de Temperatura e Idempotencia
Fuera del horario de tarifa punta, el sistema actúa como un termostato inteligente.
*   **Decisión de Estado:** Se agregaron pruebas para corroborar que una habitación se encienda si su temperatura actual es menor a la esperada, y se apague recién al superarla por el margen de histéresis (ver E).
*   **Idempotencia (Casos Borde de Red):** Para no mandarles comandos de más a los switches (que se comandan por HTTP), se agregaron casos específicos que validan que el sistema **no emita órdenes redundantes**. Si una habitación ya está `ON` y necesita calor, el `core` no envía otro comando `ON`. Si está `OFF` y ya alcanzó la temperatura, no envía otro `OFF`.

### C. Restricción Física: Límite de Potencia Eléctrica
Se implementó cobertura para proteger la instalación eléctrica general (llave térmica), asegurando que la sumatoria de potencias (kW) de las habitaciones encendidas nunca supere la potencia contratada del sitio.
*   **Partición de Superación Directa:** Un test evalúa el escenario donde dos habitaciones necesitan encenderse, pero la suma de ambas supera el límite. El sistema debe denegar el encendido de la segunda habitación.
*   **Caso Borde de Encendido Parcial:** Se evalúa un escenario con múltiples habitaciones en cola para encenderse. El sistema enciende las que entran en la potencia contratada, eligiendo la combinación que más la aprovecha (ver I), sin superar nunca el límite.

### D. Switch en estado desconocido
El simulador informa `DESCONOCIDO` mientras nadie accionó un relé, y el engine usa el mismo estado cuando no pudo consultar el switch. El `core` no supone nada: lo comanda explícitamente.
*   **En tarifa punta o sin necesidad de calor:** se ordena `OFF`.
*   **Con frío y potencia disponible:** se ordena `ON`.
*   **Con frío y sin potencia:** se ordena `OFF`, porque podría estar encendido y superar el tope.

### E. Histéresis
Una habitación se enciende apenas baja de la temperatura esperada, pero no se apaga hasta superarla por `HISTERESIS_C` (0,5 °C). Se probaron los dos lados del margen: encendida a +0,3 °C sigue encendida; a +0,5 °C se apaga. Sin el margen, una lectura que oscila alrededor del objetivo prendía y apagaba el relé en cada ciclo.

### F. Prioridad y orden de las órdenes
*   Con potencia escasa y combinaciones que aprovechan lo mismo, se enciende primero la habitación más lejos de su objetivo, sin importar su posición en el inventario.
*   Las órdenes `OFF` van siempre antes que las `ON`, para liberar potencia antes de volver a ocuparla.

### G. Contratada que baja y redondeo
*   Si lo encendido ya no entra en la contratada (bajó con un `PUT /sitio` en caliente), siguen encendidas las que más la aprovechan y se apaga el resto, empezando por la más cerca de su objetivo; la apagada no se vuelve a encender en el mismo ciclo. Con 3,5 kW encendidos y 2,5 contratados, apagar la más cerca (1,5 kW) dejaría 2,0 en uso; se apaga una de 1,0 y quedan 2,5. Con la contratada por debajo de todo, se apaga todo.
*   Las potencias se comparan con una tolerancia de redondeo: 1,1 + 2,2 kW (3,3000000000000003 en `double`) entra en 3,3 kW, tanto para seguir encendida como para encender.

### H. Punta anticipada
*   Si la punta empieza antes de la próxima evaluación, se apaga ya y no se enciende nada. Dos minutos antes (con un minuto de anticipación) sigue el control normal. Sin anticipación, cuenta solo la hora actual.

### I. Aprovechamiento de la contratada
Encender una por una, por prioridad, puede dejar potencia sin usar (el escenario F05 de `ecowarm-run` usaba el 67 % de lo que podía). Se elige la combinación que más potencia usa.
*   **El sitio de F05:** 2 kW y cuatro habitaciones empatadas de 1,2, 0,9, 0,9 y 1,0 kW. Se encienden 0,9 + 1,0 (1,9 kW), no la de 1,2 sola (que dejaba 0,8 kW donde no entraba ninguna).
*   **Empate en potencia:** entre combinaciones que suman lo mismo, la que incluye a las más lejos de su objetivo.
*   **Con algo ya encendido:** la potencia que queda libre se llena con la combinación que mejor entra, aunque deje afuera a una habitación con más frío que sola aprovecharía menos.
*   **Muchas habitaciones:** se combinan las 16 de más prioridad (`MAX_A_COMBINAR`); las demás se suman después, si entran.

## 3. Tests del módulo `engine`
El `engine` se prueba en capas, con el tiempo fijado por un reloj inyectable para que cada caso sepa si está dentro o fuera de la tarifa punta:
*   **`EvaluadorJobServiceTest`:** el ciclo de control completo con repositorios y switches simulados. Cubre reglas fuera de punta, límite de potencia, tarifa punta (también con reloj acelerado), fail-safe de termostatos (sin lecturas o vencidas, también con aceleración x600, donde el límite de 3 lecturas perdidas al ritmo de cada termostato evita apagados falsos, incluso con termostatos que publican más lento), que el estado del switch se relea en cada ciclo (un cambio externo se corrige), que una falla al consultar o comandar un switch no envenene ciclos siguientes ni frene a los demás, que un `OFF` que falla haga postergar un `ON` que con esa habitación no entraría (y que el `ON` salga cuando el apagado se confirma, o igual si entra), que una habitación cuyo switch rechaza el `ON` deje su potencia a otra durante 4 ciclos si sigue apagada (y siga en el reparto si quedó encendida), y que se reintente después o al iniciar el control, que la punta se anticipe un ciclo antes de empezar, que los switches se consulten a la vez (uno que no contesta no demora a los demás), que los `OFF` salgan a la vez y los `ON` recién cuando terminaron, que un `DETENER` que llega mientras se consultan los switches o mientras salen los `OFF` no deje salir ninguna orden más, y que una falla que dura varios ciclos se avise solo al empezar y al terminar.
*   **`ApiRestTest`:** la API REST contra el contrato `controlador.openapi-v2.yaml`: API key, `404`/`400`/`503` con el cuerpo de error uniforme, validación completa del inventario y comandos de control.
*   **`ValidadorInventarioTest`:** cada regla del §1 (campos obligatorios, rangos, formato de la franja, `desde < hasta`, unicidad de `id`, `idTermostato` e `idSwitch`).
*   **`RepositoriosPostgresTest`:** el SQL contra un PostgreSQL real (Testcontainers): ida y vuelta exacta del inventario, conservación del histórico al reemplazarlo, ritmo de cada termostato (mediana robusta a cortes y mensajes repetidos), que la última lectura sea la que llegó última aunque su hora simulada sea anterior (un `INICIAR` puede volver el reloj atrás), atomicidad ante una falla, fechas en UTC, uso del índice y migración de bases de versiones anteriores (incluida la que tenía la tabla de switches pendientes de apagar). Sin Docker se omite.
*   **`MqttSubscriberServiceTest`, `SwitchClientServiceTest`, `SimuladorRegistroServiceTest`, `RelojControlTest`, `FallasActivasTest`:** la suscripción MQTT exacta a los topics declarados (también fuera de la convención), su ajuste en cada `PUT /sitio` y tras una reconexión, que se descarten los mensajes fuera del contrato del escenario F04 de `ecowarm-run` (texto que no es JSON, sin `tC`, `tC` como texto, −40 °C y 85 °C) con un solo aviso por termostato, que los bordes del rango plausible (−10 y 50 °C) sí sean lecturas, la interpretación de las respuestas del simulador, el uso del `urlSwitch` de cada habitación (o de `SWITCHES_URL`), el estado conocido que evita consultar cada switch en cada ciclo (vence a los 5 s, se actualiza con cada comando y se descarta ante una falla), que un switch que acepta la conexión y no contesta se dé por fallado al segundo (contra un servidor HTTP real que no responde), que una consulta fallida o ilegible deje como encendido lo que estaba encendido (y como desconocido lo que estaba apagado o un `OFF` que no se confirmó), el mantenimiento del registro (incluido que se registre de nuevo cuando vuelve un simulador que no estaba corriendo, aunque no ofrezca el listado, y no ante un simulador que solo tarda en contestar) y que los avisos de falla no se repitan en cada ciclo.

## 4. Test de punta a punta
`PuntaAPuntaTest` (en `modules/engine/src/test/java/com/ioteste/engine/e2e/`) levanta el engine completo (API REST, registro, suscripción MQTT, ciclo de control y base) y lo prueba desde afuera, como lo haría el simulador de la cátedra: carga el sitio con `PUT /sitio`, arranca el control con `POST /control/comandos` y mira qué comandos reciben los switches y cuándo.

| Pieza | En el test |
|---|---|
| PostgreSQL | Real (Testcontainers, `postgres:15-alpine`) |
| Broker MQTT | Mosquitto real (Testcontainers, `eclipse-mosquitto:2`) |
| Simulador: registro (§6) y switches (§3 y §4) | `SimuladorFalso`: servidor HTTP (un hilo por pedido, como el de la cátedra) que exige la clave, guarda el estado de cada switch, registra cada comando (y bajo qué ruta base llegó, para simular switches en otro servidor), cuenta las consultas, anota la mayor potencia encendida a la vez, puede "caerse" y puede "perder los OFF" (la conexión queda colgada más que el timeout del engine). Además, cada switch puede fallar a su manera: error al encender o al apagar, consulta no disponible (`503`), estado en minúsculas, respuesta en texto que no es JSON, o lento (contesta después del timeout del engine, pero obedece) |
| Termostatos (§2) | `Termostatos`: publican cada 0,5 s con el payload del estándar; uno puede callarse o publicar un mensaje fuera del contrato. Para escalar la casa hay 20 habitaciones más (`hab01` a `hab20`) |

El engine se configura como en producción: el broker no se le indica, lo toma de la respuesta del registro. Corre a ×600 (un ciclo cada 0,5 s reales, 10 minutos simulados por segundo) un sábado, para que la tarifa punta no interfiera salvo en su propio escenario.

| Tipo | Escenario | Qué verifica |
|---|---|---|
| Normal | Arranque | Se registra con su etiqueta y su clave, se conecta al broker anunciado y recibe las lecturas de los 3 topics, incluido uno fuera de la convención |
| Normal | Encendido y apagado | Con frío enciende y no hay apagados falsos del fail-safe durante 30 minutos simulados. A +0,2 °C de la esperada sigue encendida (histéresis) y a +0,6 °C se apaga. Solo dos comandos en total |
| Normal | Reparto de potencia | Con 3,5 kW pedidos y 3,0 contratados enciende 2,5 kW: entre las dos combinaciones que suman eso, la de las habitaciones más lejos de su objetivo. Cuando una llega, la potencia liberada pasa a la que esperaba. Ni por un instante se superan los 3,0 kW |
| Normal | Switches en servidores distintos | Cada switch se comanda en el `urlSwitch` de su habitación: room3 tiene el suyo en `/edificio-b` |
| Normal | Menos consultas | En 12 ciclos consulta cada switch 1 a 3 veces, no 12. Si alguien enciende un switch a mano, igual lo detecta y lo corrige |
| Normal | Tarifa punta | Un martes a las 16:55 simuladas enciende; apaga en el último ciclo antes de las 17:00 (no después, ni mucho antes) y no vuelve a encender aunque siga con frío |
| Normal | Contratada que baja | Con 2,5 kW encendidos, un `PUT /sitio` en caliente baja la contratada a 1,2: apaga lo que sobra y queda lo que entra |
| Falla | Termostato callado | Su habitación se apaga tras 3 lecturas perdidas, no antes; las demás siguen. Al volver a publicar, se enciende de nuevo |
| Falla | Termostatos fuera de contrato | Uno publica −40 °C y otro `tC` como texto (`"15.0"`): ninguno es una lectura, y las dos habitaciones se apagan como si sus termostatos estuvieran callados. Al volver a mandar lecturas válidas, se encienden de nuevo |
| Falla | Simulador caído | El ciclo y la API siguen funcionando; al volver el simulador, el switch se corrige al estado que corresponde |
| Falla | Broker caído | Sin lecturas, el fail-safe apaga. Al volver el broker, el engine se reconecta, se vuelve a suscribir solo y sigue controlando |
| Falla | Base caída | La API sigue respondiendo. Al volver la base, las lecturas se guardan de nuevo, el control se recupera solo y el sitio se conserva |
| Falla | Habitación retirada encendida | Su switch no se vuelve a consultar ni a comandar: ya no es del sitio vigente. El control sigue con las demás |
| Falla | Se pierden los `OFF` | Hay que apagar una para encender otra, pero los `OFF` quedan colgados: no enciende la otra mientras no puede apagar. Cuando vuelven, apaga y enciende; nunca se pasó de la contratada |

**Escenarios de la lista del profesor que el runner no trae.** El `ecowarm-run.jar` trae 9 escenarios (E02, E04, E11, E14, E18, E19, F01, F03 y F05) y solo corre los firmados por la cátedra. Los demás de la lista del profesor se simularon acá a partir de su título; lo que verifican es nuestra interpretación, no el escenario oficial (ver R1 en el [roadmap](../arquitectura/roadmap.md)). Dos de ellos encontraron fallas del engine, que se corrigieron en el PR #32 ([D21](../arquitectura/decisiones.md#d21-un-switch-que-rechaza-el-on-sale-del-reparto-unos-ciclos) y [D22](../arquitectura/decisiones.md#d22-la-reconexión-al-broker-espera-como-mucho-4-s-entre-intentos)).

| Escenario | Qué simula | Qué verifica |
|---|---|---|
| E15 | La casa crece de 5 a 20 habitaciones | Se suscribe a los topics nuevos y, con 6,0 kW contratados, enciende entre 4,8 y 6,0 kW de las nuevas sin pasarse nunca; las 5 de antes, tibias, siguen apagadas |
| E16 | La casa se achica de 20 a 5 habitaciones | Las 15 que salieron no se vuelven a consultar ni a comandar, las 5 que quedan reciben calor y en el histórico quedan solo esas 5: el de las que salieron se borra con ellas ([L6](../arquitectura/roadmap.md)) |
| E17 | Arranque en frío con los 20 switches en estado desconocido | Cada switch termina en `ON` u `OFF`, sin pasarse de la contratada en ningún momento |
| F02 | Broker caído media hora simulada (×60) | El fail-safe apaga una sola vez y no enciende nada durante la caída. Al volver el broker, vuelve a controlar en menos de 15 s (antes del arreglo, 46 s) |
| F06 | Un switch da error al apagarlo | Su potencia sigue contando como ocupada y no se enciende otra en su lugar; cuando el switch se arregla, se apaga y entra la otra |
| F07 | Switches fuera del contrato: estado en minúsculas, respuesta en texto y uno lento | Las tres habitaciones reciben calor, no se pasa de la contratada y a los switches que se encendieron no se les repite el `ON` aunque su estado no se entienda. Con la contratada justa no enciende ninguna ([L16](../arquitectura/roadmap.md)) |
| F08 | Un switch encendido que no responde la consulta (`503`) | No supone su estado: lo apaga, y lo vuelve a encender cuando la habitación tiene frío |
| F09 | Un switch que nunca acepta el `ON` | Su potencia no queda reservada: las otras dos habitaciones se encienden. Cuando el switch se arregla, recibe su turno sin pasarse (antes del arreglo, la tercera habitación no se encendía nunca) |

*   **Independencia:** antes de cada escenario el control se detiene, los switches se apagan, se recarga el sitio y las 3 habitaciones quedan tibias. Las esperas son por condición (Awaitility), sin pausas fijas.
*   **Detecta regresiones reales:** se introdujeron a propósito dos errores. Si el engine no se vuelve a suscribir al reconectarse, falla el escenario del broker. Si el `core` enciende antes de apagar, falla el del reparto de potencia (el simulador falso registra 3,5 kW con 3,0 contratados). Si se vuelve a consultar cada switch en cada ciclo, falla el de las consultas; si se ignora el `urlSwitch`, falla el de los servidores distintos.
*   **Estabilidad:** los 14 primeros escenarios pasaron 5 corridas seguidas. Con los 8 de la lista del profesor, el test tarda unos 3 minutos.
*   **Requiere Docker:** sin él se omite, igual que los tests de repositorios. En GitHub Actions lo corre el job `tests` (ver abajo).

## 5. Integración continua
El workflow [`.github/workflows/maven.yml`](../../.github/workflows/maven.yml) corre en cada pull request y en cada push a `main`, con dos jobs:
*   **`tests`:** `mvn verify` directamente en el runner de GitHub, que tiene Docker. Corren los 234 tests (incluidos los que usan PostgreSQL y Mosquitto reales), Checkstyle y el umbral de cobertura del `core`. Después **falla si algún test se omitió**: sin Docker, los tests con Testcontainers se omiten en silencio y el build quedaría en verde sin haberlos corrido. Si algo falla, los reportes de Surefire quedan como artefacto del job durante 7 días.
*   **`build-and-verify`:** construye las imágenes Docker (`scripts/build.sh`), lo mismo que se despliega.

## 6. Evidencia final
La corrida final de la Iteración 6 está en [`evidencia-final/`](evidencia-final/README.md): `mvn clean verify` (234 tests), los 9 escenarios de `ecowarm-run` (62 de 62 expectativas) y el checker `ecowarm-check` (8 de 8), con los reportes completos.
