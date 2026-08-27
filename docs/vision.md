# Documento de Visión del Producto
**Proyecto:** Prototipo Exploratorio de Domótica - IoTEste
**Iteración:** 2 (Versión 2)
**Fecha:** Iteración 2

---

## 1. Declaración de Visión (Plantilla de Moore)

* **PARA** hogares y oficinas con calefacción por losa radiante.
* **QUE** buscan maximizar su confort y también optimizar su consumo eléctrico.
* **EL** software IoTEste EcoWarm.
* **ES UN** componente de una solución de domótica.
* **QUE** realiza la gestión inteligente de sensores y switches.
* **A DIFERENCIA DE** otras soluciones de domótica que sólo automatizan algunas acciones.
* **NUESTRO PRODUCTO** realiza la gestión del consumo eléctrico en forma inteligente optimizando el consumo según las tarifas disponibles en nuestro mercado.

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