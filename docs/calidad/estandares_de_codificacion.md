# Estándares de Codificación - Proyecto EcoWarm

Este documento define el conjunto de reglas y buenas prácticas adoptadas por el equipo de desarrollo para garantizar la mantenibilidad, legibilidad y calidad del código fuente del sistema domótico EcoWarm.

## 1. Convenciones de Nomenclatura
* **Clases e Interfaces:** Se utiliza `PascalCase` (ej. `EvaluadorJobService`, `TermostatoRotoException`).
* **Métodos y Variables:** Se utiliza `camelCase` (ej. `iniciarMotor`, `temperaturaActual`).
* **Constantes:** Se utiliza `UPPER_SNAKE_CASE` (ej. `TIEMPO_MAXIMO_ESPERA`, `FACTOR_ACELERACION_DEFAULT`).
* **Paquetes:** Se utiliza minúsculas en formato jerárquico inverso (ej. `com.ioteste.engine.service`).

## 2. Estructura y Arquitectura
* **Separación de Responsabilidades:** El sistema está dividido en módulos independientes (`core` y `engine`). El `core` maneja exclusivamente la lógica de dominio puro (reglas de negocio), mientras que el `engine` gestiona la infraestructura, el ciclo de control y las integraciones (REST/MQTT).
* **Inyección de Dependencias:** Se utiliza el contenedor de Spring (IoC) mediante constructores (evitando el uso excesivo de `@Autowired` en atributos) para facilitar el testing.

## 3. Manejo de Errores y Resiliencia
* **Patrón Fail-Safe:** Ninguna falla en un dispositivo individual (ej. timeout de termostato) debe detener el ciclo de control general. Los errores se capturan, se aíslan y se registran en los logs.
* **Excepciones:** Se prioriza la creación de excepciones personalizadas para el dominio (ej. `DispositivoInaccesibleException`) sobre las genéricas (`RuntimeException`).

## 4. Estilo y Formato
* **Indentación:** 4 espacios por nivel de indentación (sin tabuladores).
* **Longitud de línea:** Máximo de 120 caracteres para evitar el desplazamiento horizontal excesivo.
* **Comentarios:** El código debe ser auto-documentado a través de nombres descriptivos. Los comentarios se reservan para explicar el "por qué" de decisiones de diseño complejas, reglas de negocio específicas o parches (workarounds).

## 5. Pruebas y Cobertura
* **Frameworks:** Se utiliza JUnit 5 para la ejecución de pruebas y Mockito para el aislamiento de dependencias.
* **Cobertura Mínima (Core):** Se exige un umbral mínimo de cobertura del **85%** en el módulo `core`, validado a través de JaCoCo.
* **Tests de Integración:** Los flujos críticos (límite de potencia, tolerancia a fallos de red) deben estar cubiertos por pruebas de integración end-to-end.

## 6. Evidencia de Cumplimiento
El equipo asegura el cumplimiento de estos estándares mediante:
* **Herramientas de Análisis Estático:** Uso de extensiones como SonarLint en el IDE (VS Code) durante la etapa de escritura de código.
* **Validación Automática:** Los pipelines locales de Maven (`mvn clean test`) fallan si los tests no pasan o si no se alcanza el umbral de cobertura configurado por JaCoCo.