# Limitaciones y roadmap

Qué no hace EcoWarm hoy, qué riesgos asume y qué conviene hacer primero si se sigue desarrollando. Las limitaciones salen de la [Vista Física](4mas1.md#4-vista-física), de las consecuencias de las [decisiones](decisiones.md) y de lo que vimos al probarlo contra el simulador de la cátedra.

Ninguna impide pasar los escenarios del curso. Importan para usarlo en una casa real o para el equipo que lo reciba.

## 1. Estado al cierre de la Iteración 6

| Validado | Cómo |
|---|---|
| Las reglas, el ciclo de control, la API y la persistencia | 234 tests (37 en `core`, 165 en `engine` y 32 en `mcp`), con PostgreSQL y Mosquitto reales y 22 escenarios de punta a punta |
| Los escenarios de la cátedra que tenemos | `ecowarm-run`: 9 de 9 (E02, E04, E11, E14, E18, E19, F01, F03 y F05) y 62 de 62 expectativas |
| Los escenarios que corrió el profesor el 4/11 | Pasamos los tres; fuimos el único de los cuatro grupos. La única ✗, de nivel *mejora* en F04, se arregló en el PR #23 |
| El build | El CI corre `mvn verify` en cada PR y falla si se omite algún test |

Lo que **no** está validado: después del PR #23 no volvimos a correr F04, F07 y F08, porque el runner que tenemos no los trae (ver R1). Los arreglos de ese PR sí tienen sus tests.

## 2. Limitaciones conocidas

### Despliegue

| # | Limitación | Impacto |
|---|---|---|
| L1 | El `docker-compose.yml` no declara `host.docker.internal`. Docker Desktop (Windows y macOS) lo resuelve solo; Docker Engine en Linux no | En Linux, la configuración de ejemplo no llega al simulador |
| L2 | Un solo engine por sitio: el reloj simulado, el estado conocido de los switches y el ciclo de control viven en memoria | No se puede replicar para tener alta disponibilidad |
| L3 | Si el engine se reinicia, el sitio se conserva, pero el control queda detenido hasta el próximo `INICIAR` | En una casa real, un reinicio dejaría la calefacción sin control |
| L4 | No hay endpoint de salud ni política de reinicio: Compose solo verifica que PostgreSQL esté sano | Si el engine se cae o se cuelga, nada lo reinicia ni lo avisa |

### Datos

| # | Limitación | Impacto |
|---|---|---|
| L5 | El histórico de temperaturas crece sin límite: nada borra lecturas viejas | A una lectura por termostato cada pocos segundos, la tabla crece sin parar |
| L6 | El histórico depende del inventario. Un `PUT /sitio` con otro `id` de sitio borra el sitio anterior, y en cascada sus habitaciones y todas sus lecturas; una habitación que sale del inventario pierde las suyas | Cambiar de sitio borra la historia que consulta el MCP |

### Seguridad

| # | Limitación | Impacto |
|---|---|---|
| L7 | Todo va por HTTP sin cifrar, también las `X-API-Key` | En una red compartida, las claves se pueden leer |
| L8 | El MCP queda abierto si no se define `MCP_API_KEY`, y PostgreSQL publica el 5432 en la máquina | Quien esté en la red puede usar las tools o intentar entrar a la base |

### Control y fallas

Son contrapartidas de decisiones que se tomaron a propósito.

| # | Limitación | Impacto | Decisión |
|---|---|---|---|
| L9 | Una habitación que sale del inventario con la calefacción encendida queda encendida | Hay que apagarla a mano | [D05](decisiones.md#d05-solo-se-comandan-los-switches-del-sitio-vigente) |
| L10 | Una habitación grande y muy fría puede esperar mientras dos más chicas aprovechan mejor la potencia. Con más de 16 habitaciones con frío, solo se combinan las 16 de más prioridad | Menos confort para esa habitación hasta que las otras llegan a su objetivo | [D11](decisiones.md#d11-con-potencia-escasa-la-combinación-que-más-aprovecha-la-contratada) |
| L11 | La histéresis (0,5 °C), el rango plausible (−10 a 50 °C), la anticipación de la punta (2 ciclos), los timeouts (3 s y 1 s), la espera después de un `ON` rechazado (4 ciclos) y el tope entre reconexiones al broker (4 s) están fijos en el código | Ajustarlos para otra casa o red exige recompilar | D10, D12, D14, D17, D21 y D22 |
| L12 | Una lectura por debajo de −10 °C se trata como sensor en falla | En una casa sin calefacción en pleno invierno, se apagaría por seguridad | [D14](decisiones.md#d14-un-mensaje-fuera-del-contrato-no-es-una-lectura) |
| L13 | Si alguien apaga un switch a mano justo mientras falla la red, el engine lo nota recién cuando la consulta vuelve a responder | Esa habitación puede quedar sin calor unos ciclos | [D15](decisiones.md#d15-el-estado-de-un-switch-se-consulta-y-nunca-se-supone) |
| L14 | Mientras un `OFF` no se confirma, su potencia sigue ocupada | Se aprovecha menos la potencia durante la falla | [D16](decisiones.md#d16-lo-que-no-se-pudo-apagar-cuenta-como-encendido) |
| L15 | Una orden que ya salió antes de un `DETENER` no se puede retirar | Puede llegar al switch después del `DETENER` | [D18](decisiones.md#d18-detener-corta-el-ciclo-en-curso) |
| L16 | Un switch que no deja leer su estado cuenta como encendido hasta que se le confirma un comando, y uno que además no confirma los `OFF` (por ejemplo, uno lento) cuenta así siempre | Con la contratada justa, puede no encenderse ninguna habitación mientras dure la falla: pasó en el F07 simulado, con 3,0 kW para 3,5. Con una contratada que alcanza para todas, el control sigue normalmente. Nunca se pasa de la contratada | [D16](decisiones.md#d16-lo-que-no-se-pudo-apagar-cuenta-como-encendido) |
| L17 | Una habitación cuyo switch rechazó el `ON` vuelve al reparto recién a los 4 ciclos | Cuando el switch se arregla, puede esperar hasta 2 minutos simulados para encenderse | [D21](decisiones.md#d21-un-switch-que-rechaza-el-on-sale-del-reparto-unos-ciclos) |
| L18 | Si el simulador se reinicia entre dos verificaciones del registro (menos de un minuto) y no ofrece el listado de controladores (como `ecowarm-check`; `ecowarm-run` sí lo ofrece), el engine no lo nota | El simulador nuevo no lo conoce hasta reiniciar el engine (`bash scripts/up.sh`) | [D23](decisiones.md#d23-ante-un-simulador-que-vuelve-el-controlador-se-registra-de-nuevo) |

## 3. Roadmap

En orden de prioridad. El esfuerzo es aproximado: **S**, menos de un día; **M**, unos días; **L**, más de una semana.

### Prioridad alta: antes de usarlo en una casa real

| # | Qué | Por qué | Por dónde empezar | Esfuerzo |
|---|---|---|---|---|
| R1 | Correr los escenarios oficiales que el runner no trae | Confirmar los arreglos de los PR #23 y #32 con los escenarios de la cátedra: E15, E16, E17, F02, F06, F07, F08 y F09 hoy están simulados a partir de su título en el test de punta a punta | Pedirle al profesor el `ecowarm-run.jar` con todos sus escenarios y correr `todos` | S |
| R2 | Retomar el control después de un reinicio | L3 | Guardar en la base la `fechaHora`, el factor y el instante del último `INICIAR`, y reanudar al arrancar (`EvaluadorJobService.iniciarMotor`, `RelojControl`) | M |
| R3 | Separar el histórico del inventario y ponerle retención | L5 y L6 | Quitar el borrado en cascada de `historico_temperatura` (`schema.sql`) y agregar una tarea que borre las lecturas de más de N días. Cuidar las consultas del MCP, que lee esa tabla ([D02](decisiones.md#d02-el-servidor-mcp-como-módulo-aparte-sin-dependencia-de-código)) | M |
| R4 | Salud y reinicio automático | L4 | `spring-boot-starter-actuator` (`/actuator/health`) en el engine y el MCP, su `healthcheck` en Compose y `restart: unless-stopped` | S |

### Prioridad media

| # | Qué | Por qué | Por dónde empezar | Esfuerzo |
|---|---|---|---|---|
| R5 | Seguridad en la red | L7 y L8 | HTTPS con un proxy inverso delante del engine y del MCP, no publicar el 5432, `MCP_API_KEY` obligatoria fuera de desarrollo local, y claves nuevas en cada despliegue: una clave vieja quedó en el historial de git | M |
| R6 | Soporte de Linux | L1 | `extra_hosts: ["host.docker.internal:host-gateway"]` en el engine y el MCP | S |
| R7 | Parámetros configurables | L11 y L12 | Pasarlos a `application.properties` y dárselos al core al construirlo, así sigue sin depender de Spring ([D01](decisiones.md#d01-las-reglas-de-control-en-un-módulo-core-de-java-puro)) | S |

### Prioridad baja

| # | Qué | Por qué | Por dónde empezar | Esfuerzo |
|---|---|---|---|---|
| R8 | Turnos por tiempo cuando una habitación espera demasiado | L10 | Que el core reciba cuánto hace que espera cada habitación. Cuidar el límite de 6 conmutaciones por hora que mide la cátedra | M |
| R9 | Imágenes versionadas | Hoy se construyen en cada máquina | Publicarlas desde el CI en un registro (por ejemplo GitHub Container Registry), con el tag de cada versión | S |
| R10 | Alta disponibilidad | L2 | Sacar de memoria el reloj, el estado conocido de los switches y el estado del control, y que un solo engine corra el ciclo a la vez | L |

## 4. Fuera del alcance de este curso

* **Dispositivos reales.** El controlador se validó solo contra simuladores (el de la cátedra, el falso de los tests y el de la demo). Con termostatos y switches reales que cumplan `interop-v2` no debería cambiar nada, pero no se probó.
* **Una interfaz para el dueño de casa.** Hoy se opera por la API REST, el simulador o un agente de IA a través del MCP.
* **Apagar los switches de una habitación retirada (L9).** Cambiarlo depende del contrato de la cátedra, que hoy lo marca como falla.
