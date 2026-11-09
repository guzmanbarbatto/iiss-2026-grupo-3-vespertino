# Fuentes de los diagramas

Diagramas de [`4mas1.md`](../4mas1.md) escritos en [Mermaid](https://mermaid.js.org/). Se edita el `.mmd` y se vuelve a generar el `.png`, que es lo que muestra el documento.

| Fuente | Imagen | Vista |
|---|---|---|
| `vista-desarrollo.mmd` | `../VistaDesarrollo.png` | Desarrollo: módulos Maven, paquetes y dependencias |
| `vista-procesos-hilos.mmd` | `../VistaProcesosHilos.png` | Procesos: hilos del engine y lo que comparten |
| `vista-procesos-ciclo.mmd` | `../VistaProcesosCiclo.png` | Procesos: un ciclo de control |

## Regenerar una imagen

Con Node.js instalado, desde `docs/arquitectura/`:

```bash
npx -y @mermaid-js/mermaid-cli@11.4.2 -i diagramas/vista-desarrollo.mmd -o VistaDesarrollo.png -b white -s 2
npx -y @mermaid-js/mermaid-cli@11.4.2 -i diagramas/vista-procesos-hilos.mmd -o VistaProcesosHilos.png -b white -s 2
npx -y @mermaid-js/mermaid-cli@11.4.2 -i diagramas/vista-procesos-ciclo.mmd -o VistaProcesosCiclo.png -b white -s 2
```

Si `mermaid-cli` no encuentra un navegador (npm 11 puede bloquear la descarga automática de Chromium), se le indica el Chrome instalado con un `puppeteer.json` y la opción `-p`. Así se generaron las imágenes actuales:

```bash
echo '{"executablePath": "C:/Program Files/Google/Chrome/Application/chrome.exe"}' > puppeteer.json
npx -y @mermaid-js/mermaid-cli@11.4.2 -p puppeteer.json -i diagramas/vista-procesos-ciclo.mmd -o VistaProcesosCiclo.png -b white -s 2
```

`vista-desarrollo.mmd` y `vista-procesos-hilos.mmd` usan el layout ELK (`layout: elk`), que ordena mejor que el de por defecto un grafo con tantos cruces. Para una vista rápida también se puede pegar el `.mmd` en [mermaid.live](https://mermaid.live).
