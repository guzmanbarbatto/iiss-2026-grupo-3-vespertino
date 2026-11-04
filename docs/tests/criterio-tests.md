# Criterios de Adecuación del Conjunto de Tests (Iteración 4)

Este documento detalla el diseño y los criterios de cobertura aplicados para las pruebas unitarias del módulo `core` (`EvaluadorCoreTest.java`), asegurando el correcto funcionamiento de las reglas de negocio críticas bajo una arquitectura guiada por el dominio.

## 1. Estrategia y Técnicas Aplicadas
El conjunto de tests fue diseñado utilizando técnicas de **Pruebas de Caja Negra**, enfocándose en los requisitos funcionales establecidos para la Iteración 4. Las técnicas principales utilizadas fueron:
*   **Partición de Equivalencia:** Para agrupar entradas válidas (ej. días hábiles vs. fines de semana, temperaturas altas vs. bajas).
*   **Análisis de Valores Límite:** Para probar el comportamiento del sistema exactamente en los bordes horarios de la tarifa punta y en los umbrales de potencia contratada.

## 2. Cobertura de Reglas de Negocio

### A. Regla 1: Tarifa Punta (Restricción Económica)
Se verificó que el sistema priorice el ahorro energético apagando todos los dispositivos durante la tarifa punta, sin importar la temperatura actual.
*   **Particiones comunes:** Se crearon tests para verificar el comportamiento dentro de la franja horaria (lunes a viernes, 17:00 a 23:00) forzando el apagado de equipos encendidos. También se evaluaron días de fin de semana para garantizar que la restricción no aplique.
*   **Casos Borde (Valores Límite):** 
    *   *Límite Inferior Inclusivo:* Se evaluó el sistema exactamente a las `17:00:00`. El test garantiza que el corte se activa de forma inmediata en el segundo inicial.
    *   *Límite Superior Exclusivo:* Se evaluó el sistema exactamente a las `23:00:00`. El test verifica que la restricción se levanta, permitiendo encender equipos nuevamente.

### B. Reglas 2 y 3: Control de Temperatura e Idempotencia
Fuera del horario de tarifa punta, el sistema actúa como un termostato inteligente.
*   **Decisión de Estado:** Se agregaron pruebas para corroborar que una habitación se encienda si su temperatura actual es menor a la esperada, y se apague en cuanto la alcance o supere.
*   **Idempotencia (Casos Borde de Red):** Para evitar saturar el broker MQTT y la red, se agregaron casos específicos que validan que el sistema **no emita órdenes redundantes**. Si una habitación ya está `ON` y necesita calor, el `core` no envía otro comando `ON`. Si está `OFF` y ya alcanzó la temperatura, no envía otro `OFF`.

### C. Restricción Física: Límite de Potencia Eléctrica
Se implementó cobertura para proteger la instalación eléctrica general (llave térmica), asegurando que la sumatoria de potencias (kW) de las habitaciones encendidas nunca supere la potencia contratada del sitio.
*   **Partición de Superación Directa:** Un test evalúa el escenario donde dos habitaciones necesitan encenderse, pero la suma de ambas supera el límite. El sistema debe denegar el encendido de la segunda habitación.
*   **Caso Borde de Encendido Parcial:** Se evalúa un escenario con múltiples habitaciones en cola para encenderse. El sistema debe sumar iterativamente la carga y encender tantas habitaciones como sea posible matemáticamente, descartando únicamente aquella unidad que provoque el desbordamiento de la potencia máxima permitida.

### D. Switch en estado desconocido
El simulador informa `DESCONOCIDO` mientras nadie accionó un relé, y el engine usa el mismo estado cuando no pudo consultar el switch. El `core` no supone nada: lo comanda explícitamente.
*   **En tarifa punta o sin necesidad de calor:** se ordena `OFF`.
*   **Con frío y potencia disponible:** se ordena `ON`.
*   **Con frío y sin potencia:** se ordena `OFF`, porque podría estar encendido y superar el tope.

### E. Histéresis
Una habitación se enciende apenas baja de la temperatura esperada, pero no se apaga hasta superarla por `HISTERESIS_C` (0,5 °C). Se probaron los dos lados del margen: encendida a +0,3 °C sigue encendida; a +0,5 °C se apaga. Sin el margen, una lectura que oscila alrededor del objetivo prendía y apagaba el relé en cada ciclo.

### F. Prioridad y orden de las órdenes
*   Con potencia escasa se enciende primero la habitación más lejos de su objetivo, sin importar su posición en el inventario.
*   Las órdenes `OFF` van siempre antes que las `ON`, para liberar potencia antes de volver a ocuparla.

## 3. Tests del módulo `engine` (Iteración 5)
El `engine` se prueba en capas, con el tiempo fijado por un reloj inyectable para que cada caso sepa si está dentro o fuera de la tarifa punta:
*   **`EvaluadorJobServiceTest`:** el ciclo de control completo con repositorios y switches simulados. Cubre reglas fuera de punta, límite de potencia, tarifa punta (también con reloj acelerado), fail-safe de termostatos (sin lecturas o vencidas, también con aceleración x600, donde el límite de 3 lecturas perdidas al ritmo de cada termostato evita apagados falsos, incluso con termostatos que publican más lento), que el estado del switch se relea en cada ciclo (un cambio externo se corrige), que una falla al consultar o comandar un switch no envenene ciclos siguientes ni frene a los demás, que los switches de habitaciones retiradas del inventario se apaguen (con reintento, y se descarten si el switch no existe en el simulador) y que una falla que dura varios ciclos se avise solo al empezar y al terminar.
*   **`ApiRestTest`:** la API REST contra el contrato `controlador.openapi-v2.yaml`: API key, `404`/`400`/`503` con el cuerpo de error uniforme, validación completa del inventario y comandos de control.
*   **`ValidadorInventarioTest`:** cada regla del §1 (campos obligatorios, rangos, formato de la franja, `desde < hasta`, unicidad de `id`, `idTermostato` e `idSwitch`).
*   **`RepositoriosPostgresTest`:** el SQL contra un PostgreSQL real (Testcontainers): ida y vuelta exacta del inventario, conservación del histórico al reemplazarlo, ritmo de cada termostato (mediana robusta a cortes y mensajes repetidos), switches pendientes de apagar guardados en la misma transacción, atomicidad ante una falla, fechas en UTC, uso del índice y migración de bases de versiones anteriores. Sin Docker se omite.
*   **`MqttSubscriberServiceTest`, `SwitchClientServiceTest`, `SimuladorRegistroServiceTest`, `RelojControlTest`, `FallasActivasTest`:** la suscripción MQTT exacta a los topics declarados (también fuera de la convención), su ajuste en cada `PUT /sitio` y tras una reconexión, la interpretación de las respuestas del simulador, el mantenimiento del registro y que los avisos de falla no se repitan en cada ciclo.
