# Documento de Visión del Producto
**Proyecto:** Prototipo Exploratorio de Domótica - IoTEste
**Iteración:** 1
**Fecha:** Iteración inicial

---

## 1. Declaración de Visión (Plantilla de Moore)

* **PARA** dueños de hogares y administradores de oficinas modernas.
* **QUE** necesitan optimizar y automatizar el alto consumo eléctrico de su calefacción por losa radiante sin sacrificar el confort ambiental.
* **EL** sistema automatizado de IoTEste (nombre en clave temporal: *SmartRadiant*).
* **ES UN** producto de software de domótica basado en IoT y protocolos de mensajería MQTT.
* **QUE** ajusta dinámicamente el estado de la calefacción basándose en lecturas ambientales en tiempo real, al mismo tiempo que audita el consumo de energía eléctrica para evitar gastos innecesarios.
* **A DIFERENCIA DE** los termostatos tradicionales aislados, o los sistemas de domótica cerrados de alto costo que obligan al usuario a depender de servicios en la nube de terceros.
* **NUESTRO PRODUCTO** capitaliza hardware estándar y abierto (dispositivos Shelly) gestionado por una arquitectura de software propia, local, escalable y contenerizada, garantizando soberanía de datos y ofreciendo una propuesta de valor única para potenciales inversores.

---

## 2. Análisis de Capacidades de los Dispositivos (Soporte a la Visión)

Para materializar esta visión, el prototipo inicial orquesta la interacción entre dos componentes de hardware clave a través de nuestro broker MQTT central:

### A. Shelly H&T Gen 3 (Sensor Ambiental)
Actúa como el **"gatillo" (input) del sistema**. 
* **Capacidad:** Monitorea de forma periódica las fluctuaciones de temperatura y humedad en la habitación.
* **Rol en la solución:** Sus mensajes MQTT en formato JSON proveen el contexto. En futuras iteraciones, nuestro software consumirá estos datos para determinar si la habitación alcanzó el umbral térmico mínimo deseado por el usuario, evitando encender la calefacción a ciegas.

### B. Shelly Pro 1PM (Actuador y Medidor)
Actúa como el **"ejecutor" (output) y auditor del sistema**.
* **Capacidad 1 (Switch):** Posee un relé capaz de interrumpir o permitir el paso de la corriente eléctrica.
* **Capacidad 2 (Sensor de Consumo):** Mide la cantidad exacta de energía (Watts) que fluye a través de él.
* **Rol en la solución:** Nuestro software le enviará un comando MQTT para encender la losa radiante eléctrica solo cuando el Shelly H&T reporte bajas temperaturas. Simultáneamente, el 1PM reportará el consumo de energía de vuelta al sistema, permitiendo calcular métricas de gasto para el usuario final.

---

## 3. Armonía con la Arquitectura Técnica (Iteración 1)

Esta visión de negocio está directamente soportada por los cimientos técnicos establecidos en esta iteración:
1. **Desacoplamiento:** Al usar **Eclipse Mosquitto** (Broker MQTT) desplegado vía Docker, garantizamos que los dispositivos Shelly y nuestra lógica de negocio no dependan directamente el uno del otro.
2. **Escalabilidad:** El suscriptor base en **Java** (empaquetado como contenedor independiente) prueba que somos capaces de capturar la telemetría JSON de los dispositivos Shelly H&T Gen 3. En la Iteración 2, este mismo componente evolucionará para persistir estos datos y eventualmente aplicar la lógica de encendido del Shelly Pro 1PM.