# Guía para el equipo receptor

Para el equipo que toma EcoWarm sin poder consultar al anterior. El [README](../README.md) dice cómo levantar el sistema; esta guía dice cómo entenderlo, cómo cambiarlo sin romperlo y cómo validarlo contra la cátedra. Todo lo que sabíamos y no está en el código está acá o en los documentos que se enlazan.

## 1. Lo que reciben

EcoWarm es el controlador de calefacción de una casa. Cada 30 segundos de tiempo simulado decide qué habitaciones calentar según su temperatura, la potencia contratada y la tarifa punta, y comanda los calefactores. Se integra con el estándar `interop-v2` de la cátedra:

| Sección del estándar | Qué define |
|---|---|
| §1 | El inventario del sitio (`PUT /sitio`) |
| §2 | Las lecturas de los termostatos por MQTT |
| §3 y §4 | Consultar y comandar switches por HTTP |
| §5 | Iniciar y detener el control (`POST /control/comandos`) |
| §6 | El registro del controlador en el simulador |

Los contratos están en [`docs/api/`](api/).

**Estado:** pasa los 9 escenarios de `ecowarm-run` sin ninguna expectativa en rojo y los escenarios que corrió el profesor. Tiene 234 tests, y el CI los corre en cada pull request. Lo que no está validado y las limitaciones conocidas están en el [roadmap](arquitectura/roadmap.md).

### Antes de empezar, pidan esto

- [ ] Acceso de escritura al repositorio en GitHub, y a sus Actions.
- [ ] Acceso al tablero de Jira, o una exportación ([metodología](metodologia.md)).
- [ ] El `ecowarm-run.jar` **más reciente** de la cátedra, con todos sus escenarios. El que usamos trae 9; el profesor corrió además F04, F07, F08 y uno de 50 habitaciones.
- [ ] El contacto de la cátedra para dudas del estándar `interop-v2`.
- [ ] Claves nuevas para `docker/.env`. No reutilicen ninguna: una vieja quedó en el historial de git.

## 2. Primera semana

| Día | Qué hacer | Resultado esperado |
|---|---|---|
| 1 | Levantar el sistema con la [puesta en marcha del README](../README.md#2-puesta-en-marcha), correr `mvn clean verify` y `ecowarm-run ... todos` | 234 tests en verde, ninguno omitido; 9 de 9 escenarios. Si algo no da, mirar los [problemas conocidos](../README.md#5-problemas-conocidos-y-cómo-resolverlos) |
| 2 | Leer el [4+1](arquitectura/4mas1.md) en este orden: Lógica, Procesos, Física, Desarrollo y Escenarios. Después, las [decisiones](arquitectura/decisiones.md) | Saber qué hace cada pieza y por qué está hecha así |
| 3 | Recorrer el código con la tabla de la sección 3, con un test abierto al lado de cada clase | Poder explicar un ciclo de control de punta a punta |
| 4 y 5 | Hacer un primer cambio chico con el circuito completo (sección 4). Candidatos: R6 (soporte de Linux) o R4 (salud y reinicio) del [roadmap](arquitectura/roadmap.md#3-roadmap) | Un PR mergeado, con el CI en verde |

## 3. Recorrido por el código

| Para entender… | Empiecen por | Y miren |
|---|---|---|
| Las reglas de control | [`EvaluadorCore.evaluarEstado`](../modules/core/src/main/java/com/ioteste/core/service/EvaluadorCore.java) | Las reglas 1 a 4 (punta, histéresis, contratada que baja y combinación) y `EvaluadorCoreTest`, que tiene un caso por regla |
| Un ciclo de control | [`EvaluadorJobService.ejecutarCicloDeControl`](../modules/engine/src/main/java/com/ioteste/engine/service/EvaluadorJobService.java) y `evaluar` | Las consultas en paralelo, los `OFF` antes que los `ON`, el `ON` postergado y el corte con `DETENER` |
| Cuándo una lectura vale | `temperaturaVigente` y `silencioMaximo`, en la misma clase | El fail-safe: 99 °C cuando un termostato calla |
| Los switches | [`SwitchClientService`](../modules/engine/src/main/java/com/ioteste/engine/service/SwitchClientService.java) | El estado conocido de 5 s, los timeouts y por qué una consulta fallida mantiene el `ON` |
| Las lecturas MQTT | [`MqttSubscriberService.procesar`](../modules/engine/src/main/java/com/ioteste/engine/service/MqttSubscriberService.java) e `interpretar` | Qué mensaje es una lectura y cuál está fuera del contrato; la suscripción exacta a los topics |
| La última lectura de cada habitación | [`HistoricoRepository.ultimasLecturas`](../modules/engine/src/main/java/com/ioteste/engine/repository/HistoricoRepository.java) | El SQL con `LATERAL`: la última por hora de llegada y el ritmo de cada termostato |
| El inventario | `SitioController`, [`ValidadorInventario`](../modules/engine/src/main/java/com/ioteste/engine/service/ValidadorInventario.java) y [`SitioRepository.guardarInventario`](../modules/engine/src/main/java/com/ioteste/engine/repository/SitioRepository.java) | La validación del §1 y la transacción. **Ojo:** cambiar de sitio borra el histórico en cascada (L6 del roadmap) |
| El registro | [`SimuladorRegistroService`](../modules/engine/src/main/java/com/ioteste/engine/service/SimuladorRegistroService.java) | Los reintentos cada 5 s y `mantenerRegistro`, que vuelve a registrarse si el simulador se reinició |
| El tiempo simulado | [`RelojControl`](../modules/engine/src/main/java/com/ioteste/engine/service/RelojControl.java) | Cómo se calcula la hora simulada a partir del `INICIAR` |
| El esquema | [`schema.sql`](../modules/engine/src/main/resources/schema.sql) | Por qué todo es `IF NOT EXISTS` o `IF EXISTS` |
| El servidor MCP | [`EcoWarmTools`](../modules/mcp/src/main/java/com/ioteste/mcp/tools/EcoWarmTools.java) | Las 7 tools, y su [4+1](mcp/4mas1-mcp.md) |
| Cómo se prueba todo junto | [`PuntaAPuntaTest`](../modules/engine/src/test/java/com/ioteste/engine/e2e/PuntaAPuntaTest.java) | 22 escenarios con PostgreSQL y Mosquitto reales, un simulador y unos termostatos falsos |

## 4. Cómo hacer un cambio

1. **Una rama desde `main`,** con nombre `Sprint<N>-<Tema>`.
2. **Primero el test, y que falle.** Si arreglan un error, escriban el test que lo reproduce antes de arreglarlo.
3. **El cambio.**
4. **`mvn clean verify`, con Docker encendido.** Tiene que pasar sin tests omitidos y sin violaciones de Checkstyle.
5. **Comprueben que el test detecta el error:** deshagan el arreglo y vean que falla. Así lo hicimos con cada arreglo de los últimos sprints.
6. **Si el cambio toca el control, corran `ecowarm-run ... todos`:** unos 18 minutos, sin ninguna expectativa en rojo.
7. **Actualicen la documentación que el cambio afecta:**
   - el README, si cambia cómo se usa;
   - el 4+1, si cambia la arquitectura;
   - `decisiones.md`, si se toma o se revierte una decisión;
   - `criterio-tests.md` y los conteos de tests, si se agregan tests;
   - el `.mmd` y su `.png`, si cambia un diagrama.
8. **Un PR con descripción:** qué cambia, por qué y cómo se verificó. Los PR #20 a #29 sirven de modelo.
9. **El CI tiene que pasar** (`tests` y `build-and-verify`). Se mergea con merge commit.

## 5. Qué cuidar

Estas reglas sostienen el comportamiento del sistema. Romper cualquiera hace fallar escenarios de la cátedra o pone en riesgo la casa.

| Regla | Por qué | Dónde está |
|---|---|---|
| `core` no depende de nada: ni Spring, ni base, ni red. Lo que necesita le llega como dato | Las reglas se prueban solas, en menos de un segundo | [D01](arquitectura/decisiones.md#d01-las-reglas-de-control-en-un-módulo-core-de-java-puro) |
| Nunca pasarse de la potencia contratada: los `OFF` antes que los `ON`, y lo que no se pudo apagar cuenta como encendido | Es una expectativa de nivel *riesgo* | [D16](arquitectura/decisiones.md#d16-lo-que-no-se-pudo-apagar-cuenta-como-encendido) |
| Nunca suponer el estado de un switch: un estado desconocido se comanda | Un cambio hecho desde afuera tiene que corregirse | [D15](arquitectura/decisiones.md#d15-el-estado-de-un-switch-se-consulta-y-nunca-se-supone) |
| Distinguir tiempo simulado y real: la punta es en tiempo simulado; el silencio de un termostato se mide en tiempo real desde que llegó la lectura | Con aceleración alta, confundirlos apaga todo por error o deja calentar a ciegas | [D09](arquitectura/decisiones.md#d09-reloj-simulado-y-ciclo-cada-30-s-simulados) y [D13](arquitectura/decisiones.md#d13-sin-lecturas-válidas-la-habitación-se-apaga-fail-safe) |
| Respetar el contrato: solo los switches del sitio vigente, nada con el control detenido, y `X-API-Key` | El runner lo verifica en el nivel *contrato*: una violación hace fallar el escenario | [D05](arquitectura/decisiones.md#d05-solo-se-comandan-los-switches-del-sitio-vigente), [D18](arquitectura/decisiones.md#d18-detener-corta-el-ciclo-en-curso) y [D03](arquitectura/decisiones.md#d03-autenticación-con-x-api-key) |
| `schema.sql` tiene que poder correr dos veces, sobre una base nueva o sobre una vieja | Se aplica en cada arranque | [D07](arquitectura/decisiones.md#d07-jdbc-y-un-esquema-idempotente-en-lugar-de-jpa) |
| Un cambio de esquema afecta también al MCP | El MCP lee las tablas del engine | [D02](arquitectura/decisiones.md#d02-el-servidor-mcp-como-módulo-aparte-sin-dependencia-de-código) |
| Una falla se avisa una vez al empezar y otra al terminar, no en cada ciclo | A ×60, un aviso por ciclo tapa el log | [D19](arquitectura/decisiones.md#d19-cada-falla-se-avisa-al-empezar-y-al-terminar) |
| `docker/.env` nunca se sube, y la consola de `ecowarm-run` muestra la clave del simulador | Las claves | [README](../README.md#3-configuración) |

## 6. Cómo validar contra la cátedra

**El runner (`ecowarm-run`)** levanta el simulador y el broker, espera que el controlador se registre y corre escenarios firmados. Cada escenario tiene expectativas de tres niveles:
- **contrato** y **riesgo**: si fallan, el escenario no pasa;
- **mejora**: se informa, pero no hace fallar el escenario.

**Cuando una expectativa falla,** el `reporte.md` dice cuál. El detalle está en la carpeta de la corrida, junto al reporte:

| Archivo | Qué tiene | Para qué lo usamos |
|---|---|---|
| `<corrida>-<id>.serie.json` | Una muestra por minuto simulado (temperaturas, estado de cada switch, potencia en uso y demanda) y cada comando que recibió | Ver en qué momento se encendió o apagó cada habitación |
| `<corrida>-<id>.interacciones.json` | Cada llamada entre el controlador y el simulador, con hora real y simulada, latencia, pedido y respuesta | Encontrar timeouts, comandos que no llegaron y el orden de las llamadas |

Con esos dos archivos encontramos, por ejemplo, por qué F05 aprovechaba solo el 67 % (la combinación elegida, no los timeouts) y qué mandaban los termostatos de F04 (−40 °C y `tC` como texto).

**Contra el simulador del profesor, en la clase:** se configura como dice la [Vista Física](arquitectura/4mas1.md#4-vista-física):
1. Las tres direcciones (`SIMULADOR_URL`, `MQTT_BROKER_URL` y `CONTROLADOR_BASE_URL` con la IP de su máquina).
2. Su clave en `SIMULADOR_API_KEY`.
3. Reiniciar con `bash scripts/up.sh`.

Para comprobar que quedaron registrados con la dirección correcta: `curl http://<IP del simulador>:<puerto>/controladores`. Después, en el log del engine, tiene que aparecer `Sitio '…' cargado` cuando el profesor arranque un escenario.

## 7. Riesgos y prioridades que reciben

Del [roadmap](arquitectura/roadmap.md), lo primero:

| Prioridad | Qué | Por qué importa |
|---|---|---|
| R1 | Correr F04, F07 y F08 oficiales | Los arreglos del PR #23 tienen sus tests, pero no se confirmaron con esos escenarios |
| R2 | Retomar el control después de un reinicio | Hoy un reinicio deja la calefacción sin control hasta el próximo `INICIAR` |
| R3 | Separar el histórico del inventario y ponerle retención | Hoy un sitio nuevo borra todo el histórico, y el histórico crece sin límite |
| R4 | Salud y reinicio automático | Hoy nada detecta ni reinicia un engine caído |

Y una regla para todo lo demás: **antes de cambiar un comportamiento, busquen su decisión en [`decisiones.md`](arquitectura/decisiones.md).** Varias cosas que parecen errores son contrapartidas elegidas a propósito. Por ejemplo, que una habitación retirada quede encendida es lo que pide el contrato.

## 8. Glosario

| Término | Qué es |
|---|---|
| Sitio | La casa: su potencia contratada, su tarifa punta y sus habitaciones (§1) |
| Inventario | El sitio completo que manda el simulador con `PUT /sitio` |
| Potencia contratada | El máximo de kW que puede haber encendido a la vez |
| Tarifa punta | La franja horaria en la que no se calienta, por ejemplo de 17:00 a 23:00 los días hábiles |
| `topicTermostato` | El topic MQTT donde publica el termostato de una habitación. Vale el declarado, aunque no siga la convención |
| `urlSwitch` | Dónde se consulta y se comanda el switch de una habitación |
| `INICIAR` y `DETENER` | Los comandos que arrancan y frenan el control (§5). `INICIAR` trae la `fechaHora` y el `factorTiempo` |
| Hora simulada | La `fechaHora` del `INICIAR` más el tiempo real transcurrido por el factor de aceleración |
| Fail-safe | Apagar una habitación cuando no hay lecturas válidas de su termostato |
| Histéresis | El margen de 0,5 °C sobre el objetivo antes de apagar, para que el relé no titile |
| Aprovechamiento | Qué porción de la potencia que se podía encender se encendió de verdad. La cátedra pide al menos 80 % |
| Escenarios E y F | Los del runner: los E son de funcionamiento normal y los F, de falla |
| MCP y tool | El protocolo con el que un agente de IA usa el sistema, y cada operación que el servidor le ofrece |
