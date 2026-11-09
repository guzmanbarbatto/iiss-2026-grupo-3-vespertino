# Escenarios de Uso de EcoWarm

Estos escenarios describen situaciones del mundo real donde el software EcoWarm aporta valor al usuario final. Son escenarios de la **visión del producto**: al cierre de la Iteración 6, ninguno está implementado todavía. Lo que el sistema sí hace está en la [Vista de Escenarios del 4+1](../arquitectura/4mas1.md#5-vista-de-escenarios) y en los escenarios de la cátedra que pasa (ver el [roadmap](../arquitectura/roadmap.md#1-estado-al-cierre-de-la-iteración-6)).

## Escenario 1: Aprovechamiento de Tarifa Valle UTE
Es pleno invierno y la familia duerme. A las 3:00 AM comienza el horario de tarifa eléctrica más económica (valle) de UTE. El sistema detecta esto y enciende automáticamente la losa radiante de los dormitorios para alcanzar los 21°C justo antes de que los habitantes despierten a las 7:00 AM, apagándose antes de que comience el horario de tarifa punta para evitar sobrecostos.

> **Estado:** no implementado. Hoy el controlador mantiene cada habitación en su temperatura y evita la tarifa punta (la apaga incluso un poco antes de que empiece), pero no conoce la tarifa valle ni precalienta para una hora dada.

## Escenario 2: Anticipación Climática
El sistema consulta de manera autónoma el pronóstico del tiempo y detecta que en las próximas 48 horas habrá un descenso abrupto de temperatura (ola de frío). En lugar de esperar a que la temperatura interior de la casa caiga drásticamente, el sistema comienza a precalentar las habitaciones principales de manera gradual. Esto evita que los radiadores funcionen a máxima potencia durante el día, mitigando picos de consumo de energía.

> **Estado:** no implementado. El sistema no consulta ningún pronóstico.

## Escenario 3: Simulación de Capacidad Térmica
Un cliente nuevo tiene una potencia contratada baja (ej. 3.7 kW) con UTE y tiene dudas sobre si puede calefaccionar toda su casa simultáneamente sin que salte la llave térmica. Utiliza el sistema EcoWarm para simular qué temperatura máxima podría alcanzar si enciende el living y un solo dormitorio al mismo tiempo, obteniendo una recomendación clara sobre la viabilidad de su configuración antes de ejecutarla.

> **Estado:** no implementado como simulación. En funcionamiento, el controlador nunca supera la potencia contratada: con potencia escasa enciende la combinación de habitaciones que más la aprovecha, y las demás esperan su turno. La demo de 10 habitaciones con 3,7 kW contratados (`scripts/demo-10-habitaciones.sh`) muestra ese reparto.
