# Evidencia final (Iteración 6)

Corrida completa del 9 de noviembre de 2026 sobre el código final de la iteración: los tests, las imágenes Docker, los escenarios de `ecowarm-run` y el checker `ecowarm-check`, uno detrás del otro y sin tocar nada en el medio.

| Prueba | Resultado | Archivo |
|---|---|---|
| `mvn clean verify` | ✅ 234 tests (37 en `core`, 165 en `engine` y 32 en `mcp`): 0 fallas y 0 omitidos. 0 violaciones de Checkstyle, cobertura del `core` cumplida | [`mvn-verify.txt`](mvn-verify.txt) |
| `ecowarm-run todos` | ✅ 9 de 9 escenarios y 62 de 62 expectativas (1 no aplica) | [`ecowarm-run/reporte.md`](ecowarm-run/reporte.md) |
| `ecowarm-check`, 180 s | ✅ 8 de 8: "el controlador se comunica como manda el estándar" | [`checker/checker.log`](checker/checker.log) |

## Qué se corrió

- **Código:** la rama `Sprint6-Registro-Simulador` (commit `b97ecb0`), que se mergea en `main` con el PR #33. Incluye los arreglos de F02 y F09 ([D21](../../arquitectura/decisiones.md#d21-un-switch-que-rechaza-el-on-sale-del-reparto-unos-ciclos) y [D22](../../arquitectura/decisiones.md#d22-la-reconexión-al-broker-espera-como-mucho-4-s-entre-intentos)) y el del registro ([D23](../../arquitectura/decisiones.md#d23-ante-un-simulador-que-vuelve-el-controlador-se-registra-de-nuevo)).
- **Entorno:** Windows 10 Pro con Docker Desktop (Docker 29.8.2). El engine, el MCP y PostgreSQL corren en Docker con las imágenes recién construidas por `scripts/up.sh`. El runner y el checker corren en la misma máquina, con Java 25.
- **Herramientas de la cátedra:** `ecowarm-run` c95d037 y `ecowarm-check` f3d46f4, las dos de `interop-v2`.

Los pasos, en orden:

```bash
mvn -B -ntp clean verify                       # 14:21 a 14:25
bash scripts/up.sh                             # 14:25 a 14:27: construye las imágenes y levanta Docker

CLAVE=$(grep '^SIMULADOR_API_KEY=' docker/.env | cut -d= -f2- | tr -d '\r')
CLAVE_CTRL=$(grep '^APP_API_KEY=' docker/.env | cut -d= -f2- | tr -d '\r')

java -jar ecowarm-run.jar --host host.docker.internal --clave "$CLAVE" --salida reporte todos     # 14:27 a 14:44
java -jar ecowarm-check.jar --anunciar host.docker.internal --clave "$CLAVE" \
     --clave-controlador "$CLAVE_CTRL" --durante 180                                              # 14:46 y 14:49
```

## `ecowarm-run`: los 9 escenarios

| Escenario | Expectativas | Lo más relevante |
|---|---|---|
| E02 1 habitación, entra todo | 7 de 7 | 100 % del tiempo a ±0,5 °C |
| E04 2 habitaciones, no entran juntas | 7 de 7 | Usó el 98,9 % de lo que podía encender |
| E11 Entrar en punta | 8 de 8 | En punta no hubo nada encendido |
| E14 Subir la temperatura de 2 habitaciones | 8 de 8 | Reaccionó a los 2 cambios dentro del plazo |
| E18 Bajar la contratada | 8 de 8 | Reaccionó dentro del plazo; usó el 98,2 % |
| E19 Un switch ya encendido | 6 de 6 | Usó el 100 % |
| F01 Se cae el broker y vuelve | 6 de 6 (1 no aplica) | Se volvió a suscribir a los 1,3 s reales |
| F03 Un termostato deja de publicar | 6 de 6 | Apagó la habitación sin lecturas pasados 15 minutos |
| F05 Se pierden los `OFF` | 6 de 6 | Retomó los 2 switches después de la falla; usó el 99,2 % |

La expectativa que no aplica es `apagaSinLecturas` en F01: ninguna habitación quedó más de 15 minutos sin lecturas, porque el engine se reconectó antes. El detalle de cada escenario (la serie minuto a minuto, los comandos y las interacciones) está en [`ecowarm-run/20261109-142708-1/`](ecowarm-run/20261109-142708-1/).

## `ecowarm-check`: el estándar, punto por punto

| | Sección | Qué verifica |
|---|---|---|
| ✅ | §6 | Se registró ante el entorno |
| ✅ | §5.1 | Aceptó el inventario en `PUT /sitio` |
| ✅ | §5.1 | `GET /sitio` devuelve lo que se le cargó |
| ✅ | §5.2 | Respondió `GET /control` |
| ✅ | §5.2 | Aceptó `INICIAR` |
| ✅ | §2 | Se suscribió al topic del termostato |
| ✅ | §3 | Comandó switches (10 comandos) |
| ✅ | §4 | Consultó el estado del switch (14 consultas) |

**El engine no se reinició entre el runner y el checker.** Al terminar el runner, el engine notó que el simulador ya no estaba, y cuando arrancó el checker se registró solo. Eso es lo que corrige [D23](../../arquitectura/decisiones.md#d23-ante-un-simulador-que-vuelve-el-controlador-se-registra-de-nuevo): antes, el checker daba 1 de 8 porque el engine nunca se registraba ante él. Del log del engine:

```
14:27:09 INFO  Registrado en el simulador como 'control-grupo-3' (http://localhost:8080)    ← ecowarm-run
14:45:09 WARN  El simulador no responde en http://host.docker.internal:9090/controladores    ← terminó el runner
14:46:00 INFO  Registrado en el simulador como 'control-grupo-3' (http://localhost:8080)    ← ecowarm-check
14:49:00 WARN  El simulador no responde en http://host.docker.internal:9090/controladores    ← terminó el checker
14:49:30 INFO  Registrado en el simulador como 'control-grupo-3' (http://localhost:8080)    ← ecowarm-check, otra vez
```

El checker se corrió dos veces seguidas (a las 14:46 y a las 14:49), y las dos dieron 8 de 8. Se guarda la segunda, porque en la primera la consola de Windows mostraba mal los acentos. El log está sin los códigos de color de la consola.

## Qué no cubre

- Los escenarios de la lista del profesor que el runner no trae (E15, E16, E17, F02, F06, F07, F08 y F09) se simularon a partir de su título en el test de punta a punta: forman parte de los 234 tests, pero no son los escenarios oficiales ([criterio de tests](../criterio-tests.md#4-test-de-punta-a-punta), R1 del [roadmap](../../arquitectura/roadmap.md)).
- Todo corrió en una sola máquina con Windows. Las limitaciones conocidas están en el [roadmap](../../arquitectura/roadmap.md).
- Los archivos no tienen ninguna clave: se revisaron contra las de `docker/.env` antes de versionarlos.
