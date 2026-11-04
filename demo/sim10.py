"""Simulador de demo con 10 habitaciones (contrato interop-v2), con un modelo térmico simple.

El simulador de la cátedra (ecowarm-check.jar) tiene el sitio fijo con 2 habitaciones; este permite ver
cómo el engine reparte 3.7 kW contratados entre 10 habitaciones que suman 10.1 kW.

- Registro (§6): al registrarse un controlador, le carga el inventario con PUT /sitio (como el simulador real).
- Switches (§3, §4): recuerdan su estado; arrancan en DESCONOCIDO.
- Termostatos (§2): publican por MQTT cada 2 s. Con el switch en ON la temperatura sube; en OFF baja.
- GET /_tablero: tabla en texto con temperatura, objetivo y estado de cada habitación (sin API key).
"""
import json
import socket
import threading
import time
import urllib.request
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

API_KEY = "clave-sim"
POTENCIA_CONTRATADA = 3.7
SUBE_POR_SEGUNDO = 0.15   # °C por segundo real con la calefacción encendida
BAJA_POR_SEGUNDO = 0.04   # °C por segundo real con la calefacción apagada
EXTERIOR = 10.0

# id, nombre, potenciaKW, temperatura esperada, temperatura inicial
HABITACIONES = [
    ("room1", "Living", 1.2, 21.5, 14.0), ("room2", "Dormitorio", 0.8, 20.0, 16.0),
    ("room3", "Cocina", 1.5, 22.0, 12.5), ("room4", "Baño", 1.0, 21.0, 18.0),
    ("room5", "Escritorio", 0.9, 20.0, 15.0), ("room6", "Comedor", 1.1, 21.5, 13.0),
    ("room7", "Lavadero", 0.7, 19.5, 17.0), ("room8", "Suite", 1.3, 22.0, 19.0),
    ("room9", "Cuarto niños", 1.0, 20.5, 14.5), ("room10", "Estudio", 0.6, 21.0, 16.5),
]


def topic(hab):
    """Topic MQTT del termostato. room10 usa uno fuera de la convención <id>/status/temperature:0,
    válido para el estándar ("el topic declarado es el que vale")."""
    return "casa/estudio/temperatura" if hab == "room10" else f"ht-{hab}/status/temperature:0"


lock = threading.Lock()
temperaturas = {h[0]: h[4] for h in HABITACIONES}
switches = {}
registros = {}


def ahora():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"


def inventario():
    return {
        "schemaVersion": "2.0",
        "sitio": {"id": "casa-demo", "nombre": "Casa demo (10 habitaciones)", "potenciaContratadaKW": POTENCIA_CONTRATADA,
                  "tarifa": {"punta": {"desde": "17:00", "hasta": "23:00", "dias": "HABILES"}}},
        "habitaciones": [{"id": i, "nombre": n, "temperaturaEsperada": esp, "potenciaKW": kw,
                          "idTermostato": "ht-" + i, "topicTermostato": topic(i),
                          "idSwitch": "sw-" + i, "urlSwitch": "http://simulador:9090"}
                         for i, n, kw, esp, _ in HABITACIONES]}


def tablero():
    en_uso = sum(kw for i, _, kw, _, _ in HABITACIONES if switches.get(i) == "ON")
    llenas = round(20 * en_uso / POTENCIA_CONTRATADA)
    lineas = [f"Casa demo · potencia en uso {en_uso:.1f} de {POTENCIA_CONTRATADA:.1f} kW  "
              f"[{'#' * llenas}{'-' * (20 - llenas)}]", "",
              f"{'HAB':<7} {'NOMBRE':<13} {'TEMP':<8} {'OBJETIVO':<9} {'SWITCH':<11} POTENCIA"]
    for i, nombre, kw, esperada, _ in HABITACIONES:
        estado = switches.get(i, "DESCONOCIDO")
        nota = "  <- calentando" if estado == "ON" else ("  (con frío, esperando turno)"
                                                         if temperaturas[i] < esperada else "")
        temp, objetivo = f"{temperaturas[i]:.1f} °C", f"{esperada:.1f} °C"
        lineas.append(f"{i:<7} {nombre:<13} {temp:<8} {objetivo:<9} {estado:<11} {kw:.1f} kW{nota}")
    return "\n".join(lineas) + "\n"


def cargar_sitio(base_url, clave):
    time.sleep(1)
    pedido = urllib.request.Request(base_url + "/sitio", data=json.dumps(inventario()).encode(), method="PUT",
                                    headers={"Content-Type": "application/json", "X-API-Key": clave})
    with urllib.request.urlopen(pedido, timeout=10) as r:
        print(f"PUT /sitio -> {r.status}", flush=True)


# ---------------------------------------------------------------- MQTT mínimo (3.1.1, QoS 0)
def largo_variable(n):
    salida = b""
    while True:
        byte, n = n % 128, n // 128
        salida += bytes([byte | (128 if n else 0)])
        if not n:
            return salida


def mqtt_conectar():
    while True:
        try:
            s = socket.create_connection(("mosquitto", 1883), timeout=10)
            cuerpo = b"\x00\x04MQTT\x04\x02\x00\x3c" + len(b"sim10").to_bytes(2, "big") + b"sim10"
            s.sendall(b"\x10" + largo_variable(len(cuerpo)) + cuerpo)
            s.recv(4)
            return s
        except OSError:
            time.sleep(2)


def termostatos():
    s = mqtt_conectar()
    ultimo = time.time()
    while True:
        time.sleep(2)
        transcurrido, ultimo = time.time() - ultimo, time.time()
        with lock:
            for hab, *_ in HABITACIONES:
                if switches.get(hab) == "ON":
                    temperaturas[hab] += SUBE_POR_SEGUNDO * transcurrido
                else:
                    temperaturas[hab] = max(EXTERIOR, temperaturas[hab] - BAJA_POR_SEGUNDO * transcurrido)
                tc = round(temperaturas[hab], 1)
                mensaje = json.dumps({"id": 0, "tC": tc, "tF": round(tc * 9 / 5 + 32, 1), "ts": round(time.time(), 3)})
                topic_mqtt = topic(hab).encode()
                cuerpo = len(topic_mqtt).to_bytes(2, "big") + topic_mqtt + mensaje.encode()
                try:
                    s.sendall(b"\x30" + largo_variable(len(cuerpo)) + cuerpo)
                except OSError:
                    s = mqtt_conectar()


# ---------------------------------------------------------------- REST
class Handler(BaseHTTPRequestHandler):
    def _responder(self, status, cuerpo):
        datos = json.dumps(cuerpo).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(datos)))
        self.end_headers()
        self.wfile.write(datos)

    def _cuerpo(self):
        if self.headers.get("Transfer-Encoding", "").lower() == "chunked":
            datos = b""
            while True:
                largo = int(self.rfile.readline().strip().split(b";")[0], 16)
                if largo == 0:
                    self.rfile.readline()
                    break
                datos += self.rfile.read(largo)
                self.rfile.readline()
        else:
            datos = self.rfile.read(int(self.headers.get("Content-Length") or 0))
        return json.loads(datos or b"null")

    def do_GET(self):
        partes = self.path.strip("/").split("/")
        with lock:
            if self.path == "/_tablero":
                datos = tablero().encode()
                self.send_response(200)
                self.send_header("Content-Type", "text/plain; charset=utf-8")
                self.send_header("Content-Length", str(len(datos)))
                self.end_headers()
                self.wfile.write(datos)
            elif self.path == "/_estado":
                self._responder(200, {"temperaturas": {k: round(v, 1) for k, v in temperaturas.items()},
                                      "switches": switches})
            elif self.headers.get("X-API-Key") != API_KEY:
                self._responder(401, {"status": 401})
            elif self.path == "/controladores":
                self._responder(200, [{"id": e, "etiqueta": e} for e in registros])
            elif len(partes) == 2 and partes[0] == "switches" and partes[1][3:] in temperaturas:
                estado = switches.get(partes[1][3:], "DESCONOCIDO")
                self._responder(200, {"idSwitch": partes[1], "estado": estado, "actualizadoEn": None})
            else:
                self._responder(404, {"status": 404})

    def do_POST(self):
        if self.headers.get("X-API-Key") != API_KEY:
            self._responder(401, {"status": 401})
            return
        partes = self.path.strip("/").split("/")
        cuerpo = self._cuerpo()
        with lock:
            if self.path == "/controladores":
                registros[cuerpo["etiqueta"]] = cuerpo
                threading.Thread(target=cargar_sitio, args=(cuerpo["baseUrl"], cuerpo["apiKey"]), daemon=True).start()
                self._responder(201, {"id": cuerpo["etiqueta"], "etiqueta": cuerpo["etiqueta"],
                                      "mqtt": {"host": "mosquitto", "puerto": 1883}})
            elif len(partes) == 3 and partes[0] == "switches" and partes[1][3:] in temperaturas:
                hab = partes[1][3:]
                cambio = switches.get(hab) != cuerpo["accion"]
                switches[hab] = cuerpo["accion"]
                self._responder(200, {"idSwitch": partes[1], "estado": cuerpo["accion"], "cambio": cambio,
                                      "aplicadoEn": ahora()})
            else:
                self._responder(404, {"status": 404})

    def log_message(self, formato, *args):
        pass


threading.Thread(target=termostatos, daemon=True).start()
ThreadingHTTPServer(("0.0.0.0", 9090), Handler).serve_forever()
