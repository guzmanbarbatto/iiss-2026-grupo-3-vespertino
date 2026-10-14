# Modelo de Arquitectura 4+1: EcoWarm (Iteración 4)

Este documento describe la arquitectura del sistema EcoWarm basándose en el modelo de vistas 4+1, haciendo especial énfasis en la **Vista Lógica** y en la separación estructural implementada en la Iteración 4.

## Vista Lógica

La arquitectura del sistema se ha rediseñado para cumplir con un enfoque Multi-Módulo, aislando completamente las reglas del negocio de las responsabilidades de infraestructura (frameworks, bases de datos y comunicaciones).

El sistema se divide en dos grandes módulos independientes:

### A. Módulo `core` (El Dominio Puro)
* **Responsabilidad:** Contiene la lógica matemática, las reglas tarifarias y las decisiones de encendido/apagado (`EvaluadorCore`).
* **Características:**
  * Es una biblioteca de Java puro (Plain Old Java Objects).
  * No tiene dependencias de Spring Boot, MQTT, bases de datos ni clientes web.
  * Representa el estado del sistema mediante objetos de transferencia (DTOs) y registros inmutables.
  * Altamente testeable mediante Desarrollo Guiado por Pruebas (TDD) y herramientas nativas como JUnit y Mockito.

### B. Módulo `engine` (La Infraestructura / Adaptadores)
* **Responsabilidad:** Actúa como el puente entre el mundo exterior (Internet, sensores, actuadores) y el cerebro del sistema (`core`).
* **Características:**
  * Está construido sobre el framework Spring Boot.
  * Maneja la persistencia relacional con PostgreSQL vía Spring Data JPA.
  * Se suscribe asíncronamente a los tópicos de Mosquitto MQTT (`MqttSubscriberService`) para registrar lecturas físicas.
  * Expone la API REST segura (con `X-API-Key`) para ser consultada/controlada externamente.
  * Se comunica como cliente HTTP hacia el Simulador de la cátedra para el registro de controladores y el envío de comandos a los relés.

---
