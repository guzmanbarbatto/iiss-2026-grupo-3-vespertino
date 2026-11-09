# Decisiones de arquitectura

Las decisiones que dieron forma a EcoWarm: qué se decidió, por qué, qué alternativas se descartaron y qué consecuencias tiene. Varias de las más recientes salieron de validar el controlador contra el simulador de la cátedra: el runner `ecowarm-run` y las corridas del profesor del 4/11.

Cada decisión indica cuándo se tomó (iteración y PR) y si sigue vigente. Las que se revirtieron están al final, con el motivo. La arquitectura resultante está en [`4mas1.md`](4mas1.md), y las limitaciones que dejan estas decisiones, con lo que conviene hacer después, en [`roadmap.md`](roadmap.md).

| # | Decisión | Cuándo |
|---|---|---|
| | **Estructura** | |
| [D01](#d01-las-reglas-de-control-en-un-módulo-core-de-java-puro) | Las reglas de control en un módulo `core` de Java puro | Iteración 4 |
| [D02](#d02-el-servidor-mcp-como-módulo-aparte-sin-dependencia-de-código) | El servidor MCP como módulo aparte, sin dependencia de código | Iteración 5 |
| | **Contrato y comunicación** | |
| [D03](#d03-autenticación-con-x-api-key) | Autenticación con `X-API-Key` | Iteración 4 |
| [D04](#d04-cada-switch-en-el-urlswitch-de-su-habitación) | Cada switch en el `urlSwitch` de su habitación | Iteración 6 (PR #22) |
| [D05](#d05-solo-se-comandan-los-switches-del-sitio-vigente) | Solo se comandan los switches del sitio vigente | Iteración 6 (PR #22) |
| [D06](#d06-suscripción-exacta-a-los-topics-del-inventario) | Suscripción exacta a los topics del inventario | Iteración 6 (PR #21) |
| | **Persistencia** | |
| [D07](#d07-jdbc-y-un-esquema-idempotente-en-lugar-de-jpa) | JDBC y un esquema idempotente en lugar de JPA | Antes del Sprint 6 (PR #20) |
| [D08](#d08-la-última-lectura-es-la-que-llegó-última) | La última lectura es la que llegó última | Iteración 6 (PR #22) |
| | **Control** | |
| [D09](#d09-reloj-simulado-y-ciclo-cada-30-s-simulados) | Reloj simulado y ciclo cada 30 s simulados | Iteración 5 |
| [D10](#d10-histéresis-de-05-c) | Histéresis de 0,5 °C | Antes del Sprint 6 (PR #20) |
| [D11](#d11-con-potencia-escasa-la-combinación-que-más-aprovecha-la-contratada) | Con potencia escasa, la combinación que más aprovecha la contratada | Iteración 6 (PR #22) |
| [D12](#d12-la-tarifa-punta-se-anticipa-dos-ciclos) | La tarifa punta se anticipa dos ciclos | Iteración 6 (PR #22) |
| | **Fallas** | |
| [D13](#d13-sin-lecturas-válidas-la-habitación-se-apaga-fail-safe) | Sin lecturas válidas, la habitación se apaga (fail-safe) | Iteración 6 (PR #21) |
| [D14](#d14-un-mensaje-fuera-del-contrato-no-es-una-lectura) | Un mensaje fuera del contrato no es una lectura | Iteración 6 (PR #23) |
| [D15](#d15-el-estado-de-un-switch-se-consulta-y-nunca-se-supone) | El estado de un switch se consulta y nunca se supone | PR #20, #22 y #23 |
| [D16](#d16-lo-que-no-se-pudo-apagar-cuenta-como-encendido) | Lo que no se pudo apagar cuenta como encendido | Iteración 6 (PR #22) |
| [D17](#d17-los-switches-se-llaman-en-paralelo-con-timeouts-cortos) | Los switches se llaman en paralelo, con timeouts cortos | Iteración 6 (PR #22 y #23) |
| [D18](#d18-detener-corta-el-ciclo-en-curso) | `DETENER` corta el ciclo en curso | Iteración 6 (PR #23) |
| [D19](#d19-cada-falla-se-avisa-al-empezar-y-al-terminar) | Cada falla se avisa al empezar y al terminar | Iteración 6 (PR #21) |
| | **Calidad** | |
| [D20](#d20-tests-con-dependencias-reales-y-un-ci-que-no-deja-pasar-tests-omitidos) | Tests con dependencias reales, y un CI que no deja pasar tests omitidos | PR #20 y #22 |

---

## Estructura

### D01. Las reglas de control en un módulo `core` de Java puro

**Iteración 4 · Vigente**

- **Contexto:** hasta la Iteración 3 el proyecto tenía tres módulos organizados por tecnología (`generador`, `subscriber` y `api`), y las reglas de control no tenían un lugar propio, separado de Spring, MQTT y la base de datos.
- **Decisión:** las reglas viven en `core` (`EvaluadorCore`), sin ninguna dependencia: reciben la hora, la potencia contratada, la tarifa y las habitaciones, y devuelven órdenes. `engine` arma esos datos y ejecuta las órdenes.
- **Alternativas descartadas:** un solo módulo separado en paquetes, más simple pero sin nada que impida que las reglas terminen importando infraestructura.
- **Consecuencias:** las reglas se prueban en menos de un segundo y se les exige 85 % de cobertura. Todo lo que el core necesita saber (cuánto falta para la punta, el estado de cada switch) tiene que llegarle como dato.

### D02. El servidor MCP como módulo aparte, sin dependencia de código

**Iteración 5 · Vigente**

- **Contexto:** queríamos que un agente de IA pudiera consultar la casa y operarla sin tocar el controlador.
- **Decisión:** `mcp` es un tercer módulo, otra aplicación Spring Boot (Spring AI), que no importa ni `core` ni `engine`. Opera por la API REST del engine, comanda los switches en su `urlSwitch` y lee el histórico directamente de la base, porque el contrato del engine no lo expone.
- **Alternativas descartadas:** meter el servidor MCP dentro del engine, que comparte la JVM con el ciclo de control y suma riesgo al componente que se evalúa.
- **Consecuencias:** se despliega, se prueba y puede fallar por separado. Lee las tablas del engine, así que un cambio de esquema tiene que tenerlo en cuenta. Su 4+1 está en [`docs/mcp/4mas1-mcp.md`](../mcp/4mas1-mcp.md).

## Contrato y comunicación

### D03. Autenticación con `X-API-Key`

**Iteración 4 · Vigente**

- **Contexto:** en la Iteración 3 se implementó un token Bearer (HU-5), pero el contrato del controlador (`controlador.openapi-v2.yaml`) pide una clave en el header `X-API-Key`.
- **Decisión:** el engine exige `X-API-Key` (`APP_API_KEY`) en toda su API y se la declara al simulador al registrarse (§6), para que el simulador pueda llamarlo. Con el simulador usa su propia clave (`SIMULADOR_API_KEY`). El MCP exige `MCP_API_KEY` solo si se define.
- **Alternativas descartadas:** el token Bearer, que el simulador de la cátedra no usa.
- **Consecuencias:** las claves viven en `docker/.env`, que no se versiona. La HU-5 todavía dice "Bearer Token" y hay que corregirla.

### D04. Cada switch en el `urlSwitch` de su habitación

**Iteración 6 (PR #22) · Vigente**

- **Contexto:** el estándar (§3 y §4) dice que cada habitación declara dónde está su switch, y los switches de un sitio pueden estar en servidores distintos. En el PR #20 se había decidido comandarlos todos en `SIMULADOR_URL`, porque la dirección que anunciaba el simulador no se alcanzaba desde la red Docker.
- **Decisión:** cada switch se consulta y se comanda en su `urlSwitch`, en el engine y en el MCP. `SWITCHES_URL` los reemplaza a todos cuando ese nombre no es alcanzable desde la red, igual que `MQTT_BROKER_URL` con el broker que anuncia el simulador.
- **Alternativas descartadas:** seguir con `SIMULADOR_URL`, que funciona con un solo servidor pero no cumple el estándar.
- **Consecuencias:** con `ecowarm-run` en la misma máquina hay que correrlo con `--host host.docker.internal`, para que los `urlSwitch` que anuncia sean alcanzables desde los contenedores.

### D05. Solo se comandan los switches del sitio vigente

**Iteración 6 (PR #22) · Vigente. Revierte una decisión del PR #21**

- **Contexto:** en el PR #21 se decidió apagar el switch de una habitación que salía del inventario con la calefacción encendida, guardando el pendiente en la base. El runner de la cátedra lo marcó como falla de *contrato* (`soloSwitchesDelSitio`) en E02 y F01.
- **Decisión:** un switch que sale del inventario no se vuelve a consultar ni a comandar. Se quitó esa función y el esquema borra su tabla (`switch_pendiente_apagado`).
- **Alternativas descartadas:** mantener el apagado, que es más prudente en una casa real pero viola el contrato con el que se evalúa el controlador.
- **Consecuencias:** si una habitación sale del inventario con la calefacción encendida, queda encendida. Apagarla es responsabilidad de quien cambia el sitio.

### D06. Suscripción exacta a los topics del inventario

**Iteración 6 (PR #21) · Vigente**

- **Contexto:** el engine escuchaba el comodín `+/status/temperature:0`. Un termostato con otro índice de sensor, o con un topic fuera de esa convención, nunca se recibía, aunque el estándar dice que "el topic declarado es el que vale".
- **Decisión:** el engine se suscribe exactamente a los `topicTermostato` del inventario: al conectarse, en cada reconexión y en cada `PUT /sitio`. El validador rechaza un topic con comodines (`+` o `#`).
- **Alternativas descartadas:** el comodín `#`, que escucharía todo el broker.
- **Consecuencias:** el engine solo recibe lo que necesita, y un cambio de inventario ajusta la suscripción sin reiniciar.

## Persistencia

### D07. JDBC y un esquema idempotente en lugar de JPA

**Antes del Sprint 6 (PR #20) · Vigente**

- **Contexto:** el engine usaba JPA sin entidades, con `ddl-auto=update`, y un `init.sql` que solo corría con una base vacía. Las consultas que importan, como la última lectura de cada habitación, son SQL específico de PostgreSQL.
- **Decisión:** acceso con `JdbcTemplate` y un `schema.sql` idempotente que se aplica en cada arranque (`spring.sql.init.mode=always`): crea lo que falta y actualiza las bases de versiones anteriores.
- **Alternativas descartadas:** Flyway o Liquibase, que suman una herramienta para un esquema de tres tablas; y JPA con `ddl-auto`, que no controla cómo se migra una base existente.
- **Consecuencias:** cada cambio del esquema tiene que poder correr dos veces (`IF NOT EXISTS`, `IF EXISTS`). La migración desde una base vieja tiene su test contra PostgreSQL real.

### D08. La última lectura es la que llegó última

**Iteración 6 (PR #22) · Vigente**

- **Contexto:** la "última lectura" era la de hora simulada mayor. Cada `INICIAR` puede volver el reloj simulado atrás, y las lecturas de una corrida anterior (18:00) tapaban a las nuevas (10:00): en E14 el fail-safe dejó todo apagado durante 3 horas.
- **Decisión:** la última lectura es la que **llegó** última (`recibida_en`, hora real de llegada), con un índice por habitación y hora de llegada. Las filas anteriores a esa columna usan su hora simulada.
- **Alternativas descartadas:** borrar el histórico en cada `INICIAR`, que perdería datos que usa el MCP.
- **Consecuencias:** el histórico guarda las dos horas: la del mensaje y la de llegada.

## Control

### D09. Reloj simulado y ciclo cada 30 s simulados

**Iteración 5 · Vigente**

- **Contexto:** el simulador acelera el tiempo (`factorTiempo`). Las reglas dependen de la hora (la tarifa punta) y hay que poder probar un día entero en minutos.
- **Decisión:** la hora la da `RelojControl`: la `fechaHora` del `INICIAR` más el tiempo real transcurrido por el factor. El ciclo corre cada 30 s simulados, con un piso de 500 ms reales.
- **Alternativas descartadas:** usar la hora del sistema, que no permite acelerar; y un ciclo fijo de 30 s reales, que a ×60 evaluaría una vez cada 30 minutos simulados.
- **Consecuencias:** con aceleración muy alta manda el piso: a ×600, un ciclo cada 5 minutos simulados. Cada plazo se piensa en tiempo simulado o real según qué mide; el silencio de un termostato, por ejemplo, combina los dos (D13).

### D10. Histéresis de 0,5 °C

**Antes del Sprint 6 (PR #20) · Vigente**

- **Contexto:** con una lectura que oscila alrededor del objetivo, el relé prendía y apagaba en cada ciclo.
- **Decisión:** una habitación se enciende apenas baja de la temperatura esperada y se apaga recién al superarla por 0,5 °C.
- **Alternativas descartadas:** apagar al alcanzar el objetivo, que hace titilar el relé; y un tiempo mínimo encendido, que no depende de la temperatura.
- **Consecuencias:** en los escenarios de la cátedra, la habitación que más conmutó lo hizo 1 vez por hora; el máximo es 6.

### D11. Con potencia escasa, la combinación que más aprovecha la contratada

**Iteración 6 (PR #22) · Vigente. Reemplaza el encendido por prioridad del PR #20**

- **Contexto:** se encendía de a una habitación, empezando por la más lejos de su objetivo. En F05, con 2 kW y habitaciones empatadas de 1,2, 0,9, 0,9 y 1,0 kW, se encendía la de 1,2 sola y quedaban 0,8 kW sin usar: 67 % de aprovechamiento, con un mínimo de 80 %.
- **Decisión:** se busca la combinación de habitaciones con frío que más potencia usa sin pasarse de la contratada. Entre combinaciones iguales, gana la que incluye a las más lejos de su objetivo. Si baja la contratada, se usa el mismo criterio para elegir cuáles siguen encendidas. La búsqueda combina las 16 de más prioridad y descarta las ramas que no pueden mejorar.
- **Alternativas descartadas:** seguir encendiendo por prioridad; y rotar por tiempo, que suma conmutaciones.
- **Consecuencias:** F05 pasó a 99,2 % y E18 a 100 %. A cambio, una habitación grande y muy fría puede esperar mientras dos más chicas aprovechan mejor la potencia; le llega el turno cuando esas pasan su objetivo.

### D12. La tarifa punta se anticipa dos ciclos

**Iteración 6 (PR #22) · Vigente**

- **Contexto:** se apagaba en el primer ciclo **después** de las 17:00. En E11 quedaron 31 s encendidos dentro de la punta (falla de *riesgo*).
- **Decisión:** si la punta empieza antes de los próximos dos ciclos, se apaga ya. El engine le pasa al core cuánto falta.
- **Alternativas descartadas:** un ciclo, que no cubre la demora del propio ciclo y de los comandos.
- **Consecuencias:** se apaga hasta dos ciclos antes (1 minuto simulado), un costo mínimo de confort.

## Fallas

### D13. Sin lecturas válidas, la habitación se apaga (fail-safe)

**Iteración 6 (PR #21) · Vigente**

- **Contexto:** sin lecturas, calentar es calentar a ciegas. El primer límite (600 s simulados) fallaba con aceleración alta: a ×120 o más, el simulador tarda más en publicar que ese plazo y cada lectura llegaba "vencida", así que se apagaba todo por error.
- **Decisión:** una habitación sin lectura vigente se evalúa con 99 °C, y las reglas normales la apagan. Una lectura vence al pasar lo mayor entre 600 s simulados y 3 lecturas perdidas al ritmo que el engine mide de cada termostato, con un piso de 2 s reales.
- **Alternativas descartadas:** un plazo fijo en tiempo real o simulado, que falla en un extremo u otro de la aceleración.
- **Consecuencias:** a ×1 son 10 minutos; a ×600, un termostato que publica cada 2 s se da por caído a los 6 s, sin apagados falsos.

### D14. Un mensaje fuera del contrato no es una lectura

**Iteración 6 (PR #23) · Vigente**

- **Contexto:** en F04 del profesor, un `"tC": -40.0` (el valor de error de un sensor) se leía como mucho frío, y un `"tC": "20.0"` se aceptaba porque Jackson convierte el texto a número sin avisar: dos habitaciones siguieron calentando 25 minutos sin lecturas válidas.
- **Decisión:** solo es lectura un objeto JSON con `tC` numérico dentro de un rango plausible para una habitación, de −10 a 50 °C. Lo demás se descarta, y si el termostato no vuelve a mandar bien, actúa el fail-safe (D13).
- **Alternativas descartadas:** aceptar cualquier número, o convertir el texto.
- **Consecuencias:** −40 °C y 85 °C (los valores de error típicos) quedan afuera. Una casa real por debajo de −10 °C se trataría como sensor en falla.

### D15. El estado de un switch se consulta y nunca se supone

**PR #20, #22 y #23 · Vigente**

- **Contexto:** el estado se guardaba en memoria y no se volvía a consultar. Un cambio hecho desde afuera (el MCP o la consola del simulador) nunca se corregía, y un switch que no respondía quedaba como `ON` para siempre.
- **Decisión:** el estado real se consulta (`GET {urlSwitch}/switches/{id}`). El último conocido se usa durante 5 s reales para no consultar en cada ciclo, y un comando aplicado lo actualiza. Un estado `DESCONOCIDO` nunca se supone apagado: se comanda explícitamente. Si la consulta falla y el último estado conocido era `ON`, se lo sigue contando encendido.
- **Alternativas descartadas:** consultar en cada ciclo, que a ×60 son dos consultas por segundo por switch; y tratar todo fallo como desconocido, que con 50 habitaciones por Wi-Fi apagaba habitaciones que estaban calentando para rotar.
- **Consecuencias:** un cambio externo se detecta en 5 s como máximo. Si alguien apaga un switch a mano justo mientras falla la red, se nota recién cuando la consulta vuelve a responder.

### D16. Lo que no se pudo apagar cuenta como encendido

**Iteración 6 (PR #22) · Vigente**

- **Contexto:** en F05 los `OFF` no llegan. Si el ciclo daba el `OFF` por hecho y encendía otra habitación, se podía pasar de la contratada.
- **Decisión:** un `OFF` que no se pudo confirmar deja el switch como desconocido, y su potencia sigue ocupada. Un `ON` que con ella no entra en la contratada se posterga al ciclo siguiente.
- **Alternativas descartadas:** suponer que el `OFF` llegó.
- **Consecuencias:** nunca se pasa de la contratada, a costa de aprovechar menos la potencia mientras dura la falla.

### D17. Los switches se llaman en paralelo, con timeouts cortos

**Iteración 6 (PR #22 y #23) · Vigente**

- **Contexto:** las llamadas iban de a una con 2 s de timeout. Diez switches colgados dejaban el control sin evaluar 40 s reales, 40 minutos simulados con ×60. Después, por Wi-Fi, 1 s de conexión cortaba justo cuando se perdía el primer paquete.
- **Decisión:** en cada tanda (consultas, `OFF`, `ON`) los switches se llaman a la vez, en hilos virtuales; los `ON` salen recién cuando terminaron los `OFF`. Cada llamada espera 3 s para conectar y 1 s para la respuesta.
- **Alternativas descartadas:** un timeout largo, que frena el ciclo; y un solo timeout de 1 s, que falla en Wi-Fi.
- **Consecuencias:** un ciclo con switches colgados tarda unos 2 s y no uno por switch. El simulador recibe varias conexiones a la vez.

### D18. `DETENER` corta el ciclo en curso

**Iteración 6 (PR #23) · Vigente**

- **Contexto:** `DETENER` cancelaba los ciclos siguientes, pero no el que ya estaba corriendo. En F07 del profesor, un ciclo que esperaba a un switch lento mandó un `ON` con el control detenido, y el contrato (§5.2) dice que un controlador detenido no comanda.
- **Decisión:** `DETENER` baja primero una marca que el ciclo en curso mira antes de cada tanda y de cada comando.
- **Alternativas descartadas:** interrumpir el hilo del ciclo, que puede dejar una llamada HTTP a medias.
- **Consecuencias:** después de un `DETENER` no sale ninguna orden nueva. Una que ya estaba en camino no se puede retirar.

### D19. Cada falla se avisa al empezar y al terminar

**Iteración 6 (PR #21) · Vigente**

- **Contexto:** con el simulador, la base o el broker caídos, el mismo aviso se repetía en cada ciclo (con ×60, varias veces por segundo) y tapaba el resto del log.
- **Decisión:** `FallasActivas` recuerda qué fallas están en curso: se avisa una vez cuando empiezan y otra cuando se recuperan. Lo mismo para un termostato que manda mensajes fuera del contrato.
- **Alternativas descartadas:** bajar esos avisos a `DEBUG`, que esconde cuándo empezó la falla.
- **Consecuencias:** el log muestra el principio y el fin de cada falla; el detalle de los reintentos queda en `DEBUG`.

## Calidad

### D20. Tests con dependencias reales, y un CI que no deja pasar tests omitidos

**PR #20 y #22 · Vigente**

- **Contexto:** un mock no detecta un error de SQL, de suscripción MQTT o de tiempos entre componentes, y varios de los errores graves del PR #20 eran de ese tipo.
- **Decisión:** los repositorios se prueban contra PostgreSQL real, y el engine completo contra PostgreSQL y Mosquitto reales, con un simulador y unos termostatos falsos (Testcontainers). El CI corre todo en un runner con Docker y **falla si se omitió algún test**.
- **Alternativas descartadas:** solo tests con mocks; y una base en memoria (H2), que no tiene el SQL de PostgreSQL.
- **Consecuencias:** los tests completos necesitan Docker; sin él se omiten en silencio, y por eso existe ese control del CI. El test de punta a punta tarda alrededor de un minuto.

---

## Decisiones que se revirtieron

| Decisión anterior | Cuándo | Por qué se cambió | La reemplaza |
|---|---|---|---|
| Token Bearer para la API | Iteración 3 | El contrato pide `X-API-Key` | D03 |
| Comandar todos los switches en `SIMULADOR_URL` | PR #20 | El estándar dice que cada habitación declara su `urlSwitch` | D04 |
| Apagar el switch de una habitación retirada del inventario | PR #21 | El runner de la cátedra lo marcó como falla de contrato | D05 |
| Encender de a una habitación, por prioridad | PR #20 | Dejaba potencia sin usar (67 % en F05) | D11 |
| La última lectura es la de hora simulada mayor | Iteración 5 | Una corrida anterior tapaba las lecturas nuevas | D08 |
