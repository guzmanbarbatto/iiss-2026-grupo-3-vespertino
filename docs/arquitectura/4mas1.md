# Modelo de Arquitectura 4+1: EcoWarm 

Este documento describe la arquitectura del sistema EcoWarm basándose en el modelo de vistas 4+1, haciendo especial énfasis en la **Vista Lógica** y en la separación estructural implementada en la Iteración 4.

## 1. Vista Lógica

La arquitectura lógica de EcoWarm se organiza en dos módulos Maven: core y engine. Esta separación permite mantener las reglas de decisión del sistema aisladas de los mecanismos utilizados para obtener información, almacenar datos y ejecutar comandos sobre los dispositivos.

### A. Módulo `core` 
El módulo core contiene la lógica de negocio encargada de determinar cuándo deben encenderse o apagarse los switches asociados a las habitaciones.   

Su componente principal es EvaluadorCore, que recibe la hora de evaluación, la potencia eléctrica contratada, la configuración de la tarifa punta y una lista de habitaciones con sus temperaturas y estados actuales.  
A partir de estos datos, aplica las siguientes reglas:
  * **Tarifa punta:** ordena apagar los switches que estén encendidos durante el horario configurado.
  * **Control de temperatura:** fuera de la tarifa punta, ordena apagar las habitaciones que alcanzaron la temperatura esperada y encender las que necesitan calor.
  * **Límite de potencia:** antes de ordenar un encendido, comprueba que la potencia consumida más la requerida por la habitación no supere la potencia contratada.
  * **Evitar comandos innecesarios:** no genera órdenes cuando el estado actual ya satisface la condición evaluada.

### B. Módulo `engine` (La Infraestructura / Adaptadores)
* **Responsabilidad:** Actúa como el puente entre el mundo exterior (Internet, sensores, actuadores) y el cerebro del sistema (`core`).
* **Características:**
  * Está construido sobre el framework Spring Boot.
  * Maneja la persistencia relacional con PostgreSQL vía JDBC (`JdbcTemplate`). El esquema (`schema.sql`) es idempotente y se aplica en cada arranque, actualizando también bases de versiones anteriores.
  * Se suscribe asíncronamente a los tópicos de Mosquitto MQTT (`MqttSubscriberService`) para registrar lecturas físicas.
  * Expone la API REST segura (con `X-API-Key`) para ser consultada/controlada externamente.
  * Se comunica como cliente HTTP hacia el Simulador de la cátedra para el registro de controladores y el envío de comandos a los relés.

## Vista de Procesos: ciclo de control

![Ciclo de control del engine](Diagrama.PNG)

Cada 30 segundos de tiempo lógico (`ahora = fechaHora + tiempoReal × factorTiempo`), el engine lee el sitio y la última temperatura de cada habitación, **consulta el estado real de cada switch** (`GET /switches/{id}`, la única fuente de verdad: el switch también puede accionarse desde el MCP o la consola del simulador), le pasa todo al `EvaluadorCore` y envía las órdenes resultantes. Un switch que no se pudo consultar o que el simulador informa como `DESCONOCIDO` se comanda explícitamente para dejarlo en un estado conocido.

---
