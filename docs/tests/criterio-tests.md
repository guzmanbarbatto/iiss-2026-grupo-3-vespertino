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