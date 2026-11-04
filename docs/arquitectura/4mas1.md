# Modelo de Arquitectura 4+1: EcoWarm 

Este documento describe la arquitectura del sistema EcoWarm basándose en el modelo de vistas 4+1, haciendo especial énfasis en la **Vista Lógica** y en la separación estructural implementada en la Iteración 4.

## 1. Vista Lógica

La arquitectura lógica de EcoWarm se organiza en dos módulos Maven: core y engine. Esta separación permite mantener las reglas de decisión del sistema aisladas de los mecanismos utilizados para obtener información, almacenar datos y ejecutar comandos sobre los dispositivos.

### A. Módulo `core` 
El módulo core contiene la lógica de negocio encargada de determinar cuándo deben encenderse o apagarse los switches asociados a las habitaciones.   

Su componente principal es EvaluadorCore, que recibe la hora de evaluación, la potencia eléctrica contratada, la configuración de la tarifa punta y una lista de habitaciones con sus temperaturas y estados actuales.  
A partir de estos datos, aplica las siguientes reglas:
  * **Tarifa punta:** ordena apagar los switches que estén encendidos durante el horario configurado, y también si la franja empieza antes de la próxima evaluación (el engine le pasa cuánto falta: dos ciclos), para que ningún calefactor quede encendido dentro de la punta.
  * **Control de temperatura:** fuera de la tarifa punta, ordena encender las habitaciones que están por debajo de la temperatura esperada y apagar las que la superan por más de 0,5 °C (histéresis, para que el relé no prenda y apague en cada ciclo).
  * **Límite de potencia:** antes de ordenar un encendido, comprueba que la potencia consumida más la requerida por la habitación no supere la potencia contratada. Con potencia escasa, enciende la combinación de habitaciones con frío que más aprovecha la potencia libre, no una por una: con 2 kW libres y habitaciones de 1,2, 0,9, 0,9 y 1,0 kW, la de 1,2 sola deja 0,8 kW donde no entra ninguna otra, mientras que la de 0,9 con la de 1,0 usan 1,9. Entre combinaciones que aprovechan lo mismo, elige la que incluye a las habitaciones más lejos de su objetivo. Si lo ya encendido no entra (porque la contratada bajó), siguen encendidas las que más la aprovechan con el mismo criterio y apaga el resto, empezando por la más cerca de su objetivo. Lo encendido no se apaga para hacerle lugar a otra: cada habitación sigue hasta superar su objetivo.
  * **Estado desconocido:** un switch en estado `DESCONOCIDO` nunca se supone apagado: se le ordena `ON` u `OFF` para dejarlo en un estado conocido.
  * **Evitar comandos innecesarios:** no genera órdenes cuando el estado actual ya satisface la condición evaluada.

El módulo utiliza objetos de dominio como HabitacionDTO, TarifaPunta, Orden y Salida para representar los datos de entrada y los resultados de la evaluación.  
La clase EvaluadorCore no necesita acceder directamente a la base de datos, comunicarse por MQTT ni realizar solicitudes HTTP. Esto permite probar las reglas de decisión de manera aislada mediante pruebas unitarias.

### B. Módulo `engine` 
El módulo engine contiene la aplicación Spring Boot y coordina la interacción entre la lógica de negocio y los componentes externos.  

Sus principales componentes son:
  * **MqttSubscriberService:** se conecta al broker MQTT y se suscribe exactamente a los `topicTermostato` que declara el inventario (sin asumir ningún formato de topic); ajusta la suscripción en cada `PUT /sitio` y en cada reconexión. Recibe los mensajes de temperatura, los deserializa y guarda las lecturas asociadas a una habitación en PostgreSQL.
  * **EvaluadorJobService:** administra la ejecución periódica del motor. Consulta la configuración del sitio, las habitaciones, las últimas temperaturas registradas y los estados de los switches. Construye los datos que necesita EvaluadorCore, ejecuta la evaluación y procesa las órdenes obtenidas.
  * **ControlController:** expone la API REST para consultar el estado del motor y enviar comandos para iniciarlo o detenerlo.
  * **SitioController:** expone la API REST para consultar y reemplazar el inventario del sitio. Valida el inventario completo (§1 de `interop-v2`) antes de persistirlo. Los switches de las habitaciones que salen no se vuelven a tocar: el controlador comanda solo los del sitio vigente.
  * **SitioRepository:** encapsula las operaciones de acceso al inventario persistido.
  * **SwitchClientService:** consulta y comanda cada switch por HTTP en el `urlSwitch` de su habitación (§3 y §4; `SWITCHES_URL` los reemplaza a todos si ese nombre no es alcanzable desde la red del engine). Recuerda el último estado conocido de cada switch durante 5 s para no consultarlo en cada ciclo.

La persistencia se realiza mediante PostgreSQL y acceso JDBC (`JdbcTemplate`). El esquema (`schema.sql`) es idempotente y se aplica en cada arranque, actualizando también bases de versiones anteriores.  
El controlador también utiliza un programador de tareas para ejecutar evaluaciones a intervalos regulares. El intervalo depende del factor de aceleración configurado, lo que permite avanzar el reloj simulado a una velocidad diferente de la real.  

![Vista Lógica](DiagramaVistaLógica.png)

## 3. Vista de Procesos

El engine es un único proceso (una JVM) con cuatro flujos que corren en paralelo. No se llaman entre sí: se coordinan a través de PostgreSQL, del reloj simulado (`RelojControl`) y de dos eventos de Spring.

![Hilos del engine y lo que comparten](VistaProcesosHilos.png)

| Flujo | Hilo | Cuándo corre | Qué hace |
|---|---|---|---|
| API REST | Hilos de Tomcat | A pedido del simulador | `PUT /sitio` valida el inventario, lo guarda en una sola transacción y publica el evento `InventarioReemplazado`. `POST /control/comandos` sincroniza el reloj simulado y programa o cancela el ciclo de control |
| Registro (§6) | Scheduler | Cada 5 s hasta registrarse; después verifica cada 1 min | Se presenta ante el simulador y vuelve a registrarse si el simulador se reinició. Publica el broker que anuncia el simulador |
| Recepción MQTT | Hilo de Paho | Por cada mensaje | Guarda cada lectura con la hora real en que llegó. Se suscribe a los topics del inventario al conectarse, en cada reconexión y con cada `InventarioReemplazado`. La primera conexión la reintenta un hilo propio (`mqtt-conexion`) cada 5 s; después Paho reconecta solo |
| Ciclo de control | Scheduler | Cada máx(500 ms, 30 s ÷ factor) reales, es decir, cada 30 s simulados | Ver [Ciclo de control](#ciclo-de-control) |

**Por qué los flujos no se pisan:**
* El scheduler tiene dos hilos, así que el ciclo de control y el registro nunca se bloquean entre sí.
* Un ciclo no arranca hasta que termina el anterior (`scheduleAtFixedRate`): si uno se demora más que el intervalo, el siguiente arranca tarde, nunca en paralelo.
* Dentro de un ciclo, las llamadas a los switches corren en hilos virtuales, uno por switch, y el ciclo espera a que terminen todas antes de seguir: ninguna llamada queda suelta de un ciclo a otro. El estado conocido de cada switch se guarda en un `ConcurrentHashMap`.
* `RelojControl` guarda la sincronización en un único objeto inmutable, publicado en un campo `volatile`: quien lo lee ve siempre un estado completo.
* La suscripción MQTT se ajusta en métodos `synchronized`, porque la pueden pedir a la vez un hilo HTTP (`PUT /sitio`) y el de Paho (una reconexión).
* El inventario se reemplaza en una transacción: el ciclo nunca ve un sitio a medio cargar.
* Una falla no corta ningún flujo. El ciclo atrapa toda excepción (si se escapara, el scheduler cancelaría las ejecuciones siguientes) y reintenta en el ciclo siguiente. Cada falla se avisa una vez al empezar y otra al recuperarse (`FallasActivas`), no en cada ciclo.

### Ciclo de control

![Ciclo de control del engine](VistaProcesosCiclo.png)

Cada 30 segundos de tiempo lógico (`ahora = fechaHora + tiempoReal × factorTiempo`), el engine lee el sitio y la última temperatura de cada habitación, **pide el estado real de cada switch**, a todos a la vez (`GET {urlSwitch}/switches/{id}`, la única fuente de verdad: el switch también puede accionarse desde el MCP o la consola del simulador), le pasa todo al `EvaluadorCore` y envía las órdenes resultantes. Para no consultar a cada switch en cada ciclo (a ×60 serían dos consultas por segundo), `SwitchClientService` recuerda el último estado conocido durante 5 s reales: un comando aplicado lo actualiza sin consultar, y una falla o un estado `DESCONOCIDO` obligan a releerlo en el ciclo siguiente. Al iniciar el control se relee el estado de todos. Un switch que no se pudo consultar o que el simulador informa como `DESCONOCIDO` se comanda explícitamente para dejarlo en un estado conocido.

Además, en cada ciclo:
* **La última lectura es la que llegó última**, no la de hora simulada mayor: cada `INICIAR` puede volver el reloj simulado atrás, y las lecturas de una corrida anterior taparían a las nuevas.
* **Una lectura vieja no se usa.** La lectura de un termostato vale mientras no hayan pasado 600 s simulados o 3 lecturas perdidas, lo que sea mayor, con un piso de 2 s. El ritmo de cada termostato se mide con sus últimas lecturas. Si la lectura venció, el engine usa 99 °C (fail-safe) y la habitación se apaga.
* **Las órdenes `OFF` van antes que las `ON`**, para liberar potencia antes de volver a ocuparla: primero salen todos los `OFF`, a la vez, y recién cuando terminan, todos los `ON`. Si un `OFF` falla, esa habitación se cuenta como encendida: un `ON` que con ella no entra en la contratada se posterga al ciclo siguiente.
* **Un switch que no contesta no frena el control.** Cada llamada espera como mucho 1 s (conexión y respuesta); después se da por fallada y se reintenta en el ciclo siguiente. Como las llamadas de cada tanda van a la vez, un ciclo con switches colgados se demora a lo sumo un timeout por tanda (consultas, `OFF`, `ON`), no uno por switch. Antes, de a una y con 2 s, diez switches colgados dejaban el control sin evaluar 40 s reales: 40 minutos simulados con ×60.

Las fuentes de los diagramas de esta vista están en [`diagramas/`](diagramas/).

## 5. Vista de Escenarios

### Escenario: ejecución acelerada del controlador

**Objetivo:** permitir que el sistema simule el paso del tiempo a una velocidad superior a la real, facilitando la evaluación de las reglas de control en distintos horarios.

- El administrador envía un comando `INICIAR` mediante `POST /control/comandos`, incluyendo la fecha y hora inicial y el factor de aceleración.
- `ControlController` valida que se haya proporcionado la fecha y hora, actualiza el estado del control e invoca `EvaluadorJobService.iniciarMotor`.
- `EvaluadorJobService` configura el reloj simulado y programa la ejecución periódica del ciclo de control.
- En cada ciclo, el servicio consulta los datos necesarios y utiliza la hora simulada para invocar las reglas de negocio de `EvaluadorCore`.
- El Core devuelve las órdenes de control correspondientes y el motor intenta ejecutarlas mediante `SwitchClientService`.
- El administrador puede detener el controlador enviando el comando `DETENER`.

**Resultado:** el sistema puede evaluar situaciones correspondientes a distintos momentos del tiempo simulado sin esperar a que transcurra todo ese tiempo en la realidad. Por ejemplo, con un factor de aceleración de `60x`, el motor programa ciclos cada 500 milisegundos reales, equivalentes aproximadamente a 30 segundos simulados por ciclo, siempre que la ejecución no supere ese intervalo.

![Vista Escenarios 1](DiagramaVistaEsc1.png)

### Escenario: pérdida o desactualización de la lectura del termostato

**Objetivo:** evitar que el controlador tome decisiones de calefacción basándose en lecturas que considera demasiado antiguas.

- El motor inicia un ciclo de evaluación y consulta la última lectura de temperatura registrada para cada habitación.
- Si no existe una lectura, o el termostato no publica desde hace más de 600 segundos simulados (o, si eso es más, desde hace 3 lecturas perdidas: el engine mide cada cuánto publica cada termostato con la mediana de los huecos entre sus últimas 5 lecturas, así que con aceleración alta no da por vencidas lecturas que todavía no les tocaba llegar), `EvaluadorJobService` utiliza el valor de respaldo `99.0 °C` como temperatura actual.
- El servicio construye el `HabitacionDTO` con los datos de la habitación y lo entrega a `EvaluadorCore`.
- El Core compara la temperatura actual recibida con la temperatura esperada y aplica sus reglas de control.
- Fuera del horario de tarifa punta, si el switch está encendido y la temperatura de respaldo supera la esperada, el Core genera una orden `OFF`.
- `EvaluadorJobService` intenta ejecutar la orden mediante `SwitchClientService`, que se comunica con el simulador de switches por HTTP.

**Resultado:** una lectura ausente o desactualizada puede provocar el apagado de la calefacción cuando estaba encendida. El valor de `99.0 °C` es un mecanismo de respaldo de la implementación actual; no representa una medición real ni garantiza que el termostato se haya desconectado.

![Vista Escenarios 2](DiagramaVistaEsc2.png)

---
