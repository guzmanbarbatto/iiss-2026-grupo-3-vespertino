### Documento de Visión del Producto
**Proyecto:** Prototipo Exploratorio de Domótica - IoTEste  
**Iteración:** 3 (Versión 3)[cite: 3, 5]  
**Fecha:** Iteración 3[cite: 3, 5]

--------------------------------------------------------------------------------

#### 1. Declaración de Visión (Plantilla de Moore)
* **PARA** hogares y oficinas con calefacción por losa radiante[cite: 19].
* **QUE** buscan maximizar su confort y también optimizar su consumo eléctrico[cite: 19].
* **EL** software IoTEste EcoWarm[cite: 19].
* **ES UN** componente de una solución de domótica[cite: 19].
* **QUE** realiza la gestión inteligente de sensores, switches y control de temperatura[cite: 3, 19].
* **A DIFERENCIA DE** otras soluciones de domótica que sólo automatizan algunas acciones[cite: 19].
* **NUESTRO PRODUCTO** realiza la gestión del consumo eléctrico de forma inteligente y ofrece interfaces de administración seguras mediante una API REST protegida[cite: 3, 19].

--------------------------------------------------------------------------------

#### 2. Análisis de Capacidades de los Dispositivos y Servicios
Para materializar esta visión, el prototipo integra comunicación asíncrona MQTT y servicios expuestos vía API REST[cite: 3, 19]:

##### A. Shelly H&T Gen 3 (Sensor Ambiental)
* **Capacidad:** Monitorea de forma periódica la temperatura y humedad[cite: 19].
* **Rol en la solución:** Publica mensajes MQTT que son capturados por el motor del Termostato para evaluar si es necesario accionar la calefacción[cite: 3, 15, 19].

##### B. Shelly Pro 1PM / Stub REST (Actuador Switch)
* **Capacidad:** Permite interrumpir o habilitar el paso de energía eléctrica[cite: 19].
* **Rol en la solución:** Expone un endpoint REST actuando como Stub, recibiendo órdenes de encendido (`ON`) y apagado (`OFF`) enviadas automáticamente por el Controlador[cite: 3, 15, 16].

##### C. API REST EcoWarm (Capa de Control y Seguridad)
* **Capacidad:** Ofrece endpoints Nivel 2 de Richardson protegidos mediante Bearer Token[cite: 3, 20].
* **Rol en la solución:** Permite la administración CRUD de las habitaciones, el envío de comandos operativos y la validación de consistencia de la base de datos[cite: 3, 20].