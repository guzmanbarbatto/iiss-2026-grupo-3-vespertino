# Documento de Visión del Producto
**Proyecto:** Controlador de calefacción EcoWarm (IoTEste), Grupo 3

**Versión:** la declaración de visión es de la Iteración 2. Las secciones 2 y 3 se actualizaron al cierre de la Iteración 6 para reflejar el sistema actual.

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

## 2. Dispositivos

La visión se pensó para dos dispositivos Shelly:

- **Shelly H&T Gen 3,** un sensor de temperatura y humedad que publica sus lecturas por MQTT en JSON. Es el "gatillo" (input) del sistema.
- **Shelly Pro 1PM,** un relé que corta o deja pasar la corriente de la losa radiante. Es el "ejecutor" (output). También mide el consumo, para calcular métricas de gasto.

**Cómo quedó:** el sistema no se integra con un modelo de dispositivo, sino con el estándar `interop-v2` de la cátedra:

| Parte | Cómo funciona | Sección del estándar |
|---|---|---|
| Termostatos | Publican su temperatura por MQTT, en el topic que declara cada habitación. El formato de la lectura (`tC`, `tF`, `ts`) es el de los Shelly H&T | §2 |
| Switches | Se consultan y se comandan **por HTTP**, en la dirección que declara cada habitación (`urlSwitch`) | §3 y §4 |

El controlador se validó contra el simulador de la cátedra, que implementa ese estándar. **Todavía no se probó con dispositivos reales,** y **la medición de consumo del 1PM no se usa:** el sistema reparte la potencia contratada a partir de la potencia declarada de cada habitación.

---

## 3. Cómo la arquitectura soporta la visión

| La visión pide… | El sistema lo resuelve con… |
|---|---|
| Gestionar sensores y switches de forma inteligente | El módulo `core`, con las reglas de control: temperatura con histéresis, tarifa punta y potencia contratada. Es Java puro y se prueba aislado |
| Optimizar el consumo según las tarifas | No enciende nada en la tarifa punta y aprovecha al máximo la potencia contratada sin superarla. Todavía no conoce los planes y los costos de UTE ni la tarifa valle |
| Integrarse con los dispositivos sin depender de ellos | El módulo `engine`: MQTT para las lecturas, HTTP para los switches, PostgreSQL para el estado, y tolerancia a fallas de cada pieza |
| Una interfaz conversacional (GenAI) | El módulo `mcp`: un servidor MCP para que un agente de IA consulte y opere la casa |

El detalle está en la [arquitectura 4+1](arquitectura/4mas1.md). Las características y los escenarios de la visión que todavía no están implementados, en [`producto/`](producto/) y en el [roadmap](arquitectura/roadmap.md).
