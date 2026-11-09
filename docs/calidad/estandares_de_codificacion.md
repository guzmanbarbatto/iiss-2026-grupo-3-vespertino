# Estándares de Codificación - Proyecto EcoWarm

Este documento define el conjunto de reglas y buenas prácticas adoptadas por el equipo de desarrollo para garantizar la mantenibilidad, legibilidad y calidad del código fuente del sistema domótico EcoWarm. Los ejemplos son del código actual.

## 1. Convenciones de Nomenclatura
* **Clases e Interfaces:** Se utiliza `PascalCase` (ej. `EvaluadorJobService`, `SwitchClientService`).
* **Métodos y Variables:** Se utiliza `camelCase` (ej. `iniciarMotor`, `temperaturaActual`).
* **Constantes:** Se utiliza `UPPER_SNAKE_CASE` (ej. `HISTERESIS_C`, `READ_TIMEOUT_MS`). Cuando la unidad no es obvia, va en el nombre: `_C` para grados, `_MS` para milisegundos, `_KW` para potencia.
* **Paquetes:** Se utiliza minúsculas en formato jerárquico inverso (ej. `com.ioteste.engine.service`).
* **Idioma:** el código, los comentarios y los mensajes de log están en español, con los términos del contrato tal como los define (`urlSwitch`, `topicTermostato`).

## 2. Estructura y Arquitectura
* **Separación de Responsabilidades:** El sistema está dividido en tres módulos (`core`, `engine` y `mcp`). El `core` maneja exclusivamente la lógica de dominio puro (las reglas de control) y no depende de nada; el `engine` gestiona la infraestructura, el ciclo de control y las integraciones (REST y MQTT); el `mcp` es un servidor aparte para agentes de IA. El detalle está en la [Vista de Desarrollo](../arquitectura/4mas1.md#2-vista-de-desarrollo).
* **Inyección de Dependencias:** Se utiliza el contenedor de Spring (IoC) mediante constructores, nunca en atributos, para facilitar el testing. Cuando una clase tiene dos constructores (uno para Spring y otro para los tests), `@Autowired` marca el de Spring.

## 3. Manejo de Errores y Resiliencia
* **Patrón Fail-Safe:** Ninguna falla en un dispositivo individual (ej. un switch que no contesta o un termostato que calla) debe detener el ciclo de control general. Los errores se capturan, se aíslan y se registran en los logs.
* **Las fallas esperables no son excepciones:** una falla de red o un dispositivo que no responde se devuelve como resultado (por ejemplo, `Resultado.FALLIDO` o `EstadoSwitch.DESCONOCIDO`) y el que llama decide qué hacer. Las excepciones propias se reservan para casos puntuales: `ServicioExternoException` en el MCP, que traduce una falla externa a un mensaje claro para el agente de IA.
* **Un aviso por falla:** una falla que dura varios ciclos se avisa una vez cuando empieza y otra cuando se recupera (`FallasActivas`), no en cada ciclo.
* **El ciclo nunca lanza:** el ciclo de control atrapa toda excepción, porque si una se escapara, el programador de tareas cancelaría los ciclos siguientes.

## 4. Estilo y Formato
* **Indentación:** 4 espacios por nivel de indentación (sin tabuladores).
* **Longitud de línea:** Máximo de 120 caracteres para evitar el desplazamiento horizontal excesivo.
* **Comentarios:** El código debe ser auto-documentado a través de nombres descriptivos. Los comentarios se reservan para explicar el "por qué" de decisiones de diseño complejas, reglas de negocio específicas o parches (workarounds).

## 5. Pruebas y Cobertura
* **Frameworks:** Se utiliza JUnit 5 para la ejecución de pruebas, Mockito para el aislamiento de dependencias, Testcontainers para PostgreSQL y Mosquitto reales, y Awaitility para esperar condiciones sin pausas fijas.
* **Cobertura Mínima (Core):** Se exige un umbral mínimo de cobertura del **85%** en el módulo `core`, validado a través de JaCoCo.
* **Tests de Integración:** Los flujos críticos (límite de potencia, tolerancia a fallos de red) están cubiertos por el test de punta a punta, con el engine completo contra PostgreSQL y Mosquitto reales.
* **Un test por arreglo:** cada error corregido lleva un test que falla si se deshace el arreglo. El criterio de cada test está en [`criterio-tests.md`](../tests/criterio-tests.md).

## 6. Evidencia de Cumplimiento
El equipo asegura el cumplimiento de estos estándares mediante:
* **Checkstyle:** aplica [`google_checks.xml`](../../google_checks.xml) en la fase `validate` de Maven, sobre el código y los tests de los tres módulos. Una sola violación corta el build.
* **Validación Automática:** `mvn clean verify` falla si un test no pasa, si hay una violación de estilo o si no se alcanza el umbral de cobertura. El CI de GitHub Actions lo corre en cada pull request y además falla si se omitió algún test.
* **En el IDE:** el equipo usaba SonarLint en VS Code mientras escribía código. Es una ayuda: lo que se exige es lo de los dos puntos anteriores.
