package com.ioteste.engine.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * El simulador de la cátedra reducido a lo que usa el engine, según {@code simulador.openapi-v2.yaml}: el
 * registro (§6), que anuncia el broker MQTT, y los switches (§3 y §4). Exige la clave del simulador en cada
 * llamada.
 *
 * <p>Los switches responden bajo cualquier ruta base ({@code /switches/...} o {@code /edificio-b/switches/...}):
 * así un {@code urlSwitch} con ruta hace las veces de otro servidor de switches, y cada comando registra en
 * qué ruta llegó. Lleva la cuenta de los comandos, de las consultas de estado y de la mayor potencia que llegó
 * a estar encendida a la vez. Puede "caerse": mientras está caído corta cada conexión sin responder, como un
 * servidor que no está. También puede "perder los OFF": la conexión de cada OFF queda colgada más que el timeout
 * del engine y el switch no se apaga (la falla F05 de {@code ecowarm-run}). Y a cada switch se le puede poner una
 * {@link Falla} propia, como las de los escenarios F06 a F09.
 */
final class SimuladorFalso implements AutoCloseable {

    private static final Pattern SWITCH = Pattern.compile("(.*)/switches/([^/]+)");
    private static final Pattern COMANDO = Pattern.compile("(.*)/switches/([^/]+)/comandos");
    /** Más que el timeout del engine (1 s): el OFF no recibe respuesta. */
    private static final long OFF_COLGADO_MS = 2_500;

    /** Cómo puede fallar un switch, además de caerse todo el simulador. */
    enum Falla {
        /** El OFF responde con un error HTTP y el switch sigue como estaba (F06). */
        OFF_CON_ERROR,
        /** El ON responde con un error HTTP y el switch sigue como estaba (F09). */
        ON_CON_ERROR,
        /** La consulta responde 503; los comandos sí funcionan (F08). */
        CONSULTA_NO_DISPONIBLE,
        /** La consulta trae el estado en minúsculas, fuera del contrato (F07). */
        ESTADO_EN_MINUSCULAS,
        /** La consulta responde texto que no es JSON (F07). */
        RESPUESTA_EN_TEXTO,
        /** Tarda más que el timeout del engine en contestar, pero obedece (F07). */
        LENTO
    }

    private final HttpServer servidor;
    /**
     * Un hilo por pedido, como el simulador de la cátedra: el engine consulta y comanda los switches a la vez, y
     * un OFF colgado no debe demorar las demás respuestas.
     */
    private final ExecutorService hilos = Executors.newCachedThreadPool();
    private final ObjectMapper json = new ObjectMapper();
    private final String clave;
    private final String brokerHost;
    private final int brokerPuerto;
    private final Map<String, Double> potenciaPorSwitch;
    private final Map<String, String> estados = new ConcurrentHashMap<>();
    private final List<Comando> comandos = new CopyOnWriteArrayList<>();
    private final List<String> consultas = new CopyOnWriteArrayList<>();
    private final List<Map<?, ?>> registros = new CopyOnWriteArrayList<>();
    private volatile boolean caido;
    private volatile boolean offPerdidos;
    private final Map<String, Set<Falla>> fallas = new ConcurrentHashMap<>();
    private double potenciaMaxima;

    /** Un comando aplicado: a qué switch, qué acción, cuándo (hora real) y bajo qué ruta base llegó. */
    record Comando(String idSwitch, String accion, Instant en, String ruta) { }

    SimuladorFalso(String clave, Map<String, Double> potenciaPorSwitch, String brokerHost, int brokerPuerto)
            throws IOException {
        this.clave = clave;
        this.potenciaPorSwitch = Map.copyOf(potenciaPorSwitch);
        this.brokerHost = brokerHost;
        this.brokerPuerto = brokerPuerto;
        reiniciar();
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", this::atender);
        servidor.setExecutor(hilos);
        servidor.start();
    }

    String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    /** Todos los switches apagados, sin historia de comandos y levantado. Los registros se conservan. */
    synchronized void reiniciar() {
        potenciaPorSwitch.keySet().forEach(id -> estados.put(id, "OFF"));
        comandos.clear();
        consultas.clear();
        potenciaMaxima = 0;
        caido = false;
        offPerdidos = false;
        fallas.clear();
    }

    /** A partir de ahora el switch falla de esas maneras, hasta {@link #sanar} o {@link #reiniciar}. */
    void fallar(String idSwitch, Falla... comoFalla) {
        fallas.put(idSwitch, Set.of(comoFalla));
    }

    void sanar(String idSwitch) {
        fallas.remove(idSwitch);
    }

    private boolean falla(String idSwitch, Falla falla) {
        return fallas.getOrDefault(idSwitch, Set.of()).contains(falla);
    }

    void caer() {
        caido = true;
    }

    void levantar() {
        caido = false;
    }

    /** Mientras dure, ningún OFF llega al switch: la conexión queda colgada y el switch sigue como estaba. */
    void perderLosOff(boolean perder) {
        offPerdidos = perder;
    }

    /** Alguien acciona el switch desde afuera (la consola del simulador, el MCP): no es un comando del engine. */
    void accionarAMano(String idSwitch, String estado) {
        estados.put(idSwitch, estado);
    }

    /** Cuántas veces se consultó el estado del switch desde el último {@link #reiniciar()}. */
    long consultas(String idSwitch) {
        return consultas.stream().filter(idSwitch::equals).count();
    }

    String estado(String idSwitch) {
        return estados.get(idSwitch);
    }

    List<Comando> comandos(String idSwitch) {
        return comandos.stream().filter(c -> c.idSwitch().equals(idSwitch)).toList();
    }

    List<Map<?, ?>> registros() {
        return List.copyOf(registros);
    }

    /** La mayor potencia (kW) que estuvo encendida a la vez desde el último {@link #reiniciar()}. */
    synchronized double potenciaMaxima() {
        return potenciaMaxima;
    }

    /** La potencia (kW) encendida ahora, contando todos los switches que conoce el simulador. */
    synchronized double potenciaEncendida() {
        return estados.entrySet().stream()
                .filter(e -> "ON".equals(e.getValue()))
                .mapToDouble(e -> potenciaPorSwitch.get(e.getKey()))
                .sum();
    }

    private void atender(HttpExchange intercambio) throws IOException {
        try (intercambio) {
            if (caido) {
                return; // se cierra la conexión sin responder
            }
            if (!clave.equals(intercambio.getRequestHeaders().getFirst("X-API-Key"))) {
                responder(intercambio, 401, Map.of("status", 401, "mensaje", "falta o no coincide X-API-Key"));
                return;
            }
            String metodo = intercambio.getRequestMethod();
            String ruta = intercambio.getRequestURI().getPath();
            Matcher comando = COMANDO.matcher(ruta);
            Matcher consulta = SWITCH.matcher(ruta);
            if ("/controladores".equals(ruta) && "POST".equals(metodo)) {
                registrar(intercambio);
            } else if ("/controladores".equals(ruta) && "GET".equals(metodo)) {
                responder(intercambio, 200,
                        registros.stream().map(r -> Map.of("etiqueta", r.get("etiqueta"))).toList());
            } else if (comando.matches() && "POST".equals(metodo)) {
                comandar(intercambio, comando.group(2), comando.group(1));
            } else if (consulta.matches() && "GET".equals(metodo)) {
                consultar(intercambio, consulta.group(2));
            } else {
                responder(intercambio, 404, Map.of("status", 404, "mensaje", "no existe " + metodo + " " + ruta));
            }
        }
    }

    private void registrar(HttpExchange intercambio) throws IOException {
        Map<?, ?> alta = json.readValue(intercambio.getRequestBody(), Map.class);
        registros.add(alta);
        responder(intercambio, 201, Map.of("id", alta.get("etiqueta"), "etiqueta", alta.get("etiqueta"),
                "baseUrl", alta.get("baseUrl"), "registradoEn", Instant.now().toString(),
                "mqtt", Map.of("host", brokerHost, "puerto", brokerPuerto)));
    }

    private void consultar(HttpExchange intercambio, String idSwitch) throws IOException {
        consultas.add(idSwitch);
        String estado = estados.get(idSwitch);
        if (estado == null) {
            responder(intercambio, 404, Map.of("status", 404, "mensaje", "no existe el switch " + idSwitch));
            return;
        }
        if (falla(idSwitch, Falla.LENTO)) {
            demorar(OFF_COLGADO_MS);
        }
        if (falla(idSwitch, Falla.CONSULTA_NO_DISPONIBLE)) {
            responder(intercambio, 503, Map.of("status", 503, "mensaje", "el switch no está disponible"));
        } else if (falla(idSwitch, Falla.RESPUESTA_EN_TEXTO)) {
            responderTexto(intercambio, estado);
        } else {
            String informado = falla(idSwitch, Falla.ESTADO_EN_MINUSCULAS) ? estado.toLowerCase(Locale.ROOT) : estado;
            responder(intercambio, 200, Map.of("idSwitch", idSwitch, "estado", informado,
                    "actualizadoEn", Instant.now().toString()));
        }
    }

    private void comandar(HttpExchange intercambio, String idSwitch, String ruta) throws IOException {
        Object accion = json.readValue(intercambio.getRequestBody(), Map.class).get("accion");
        if (!estados.containsKey(idSwitch)) {
            responder(intercambio, 404, Map.of("status", 404, "mensaje", "no existe el switch " + idSwitch));
            return;
        }
        if (!"ON".equals(accion) && !"OFF".equals(accion)) {
            responder(intercambio, 400, Map.of("status", 400, "mensaje", "accion debe ser ON u OFF"));
            return;
        }
        if (offPerdidos && "OFF".equals(accion)) {
            demorar(OFF_COLGADO_MS); // no se aplica ni se responde
            return;
        }
        if ("OFF".equals(accion) && falla(idSwitch, Falla.OFF_CON_ERROR)
                || "ON".equals(accion) && falla(idSwitch, Falla.ON_CON_ERROR)) {
            responder(intercambio, 500, Map.of("status", 500, "mensaje", "el switch no pudo cumplir " + accion));
            return;
        }
        if (falla(idSwitch, Falla.LENTO)) {
            demorar(OFF_COLGADO_MS); // obedece, pero el engine ya cortó por timeout
        }
        boolean cambio = aplicar(idSwitch, (String) accion, ruta);
        responder(intercambio, 200, Map.of("idSwitch", idSwitch, "estado", accion, "cambio", cambio,
                "aplicadoEn", Instant.now().toString()));
    }

    private synchronized boolean aplicar(String idSwitch, String accion, String ruta) {
        boolean cambio = !accion.equals(estados.put(idSwitch, accion));
        comandos.add(new Comando(idSwitch, accion, Instant.now(), ruta));
        potenciaMaxima = Math.max(potenciaMaxima, potenciaEncendida());
        return cambio;
    }

    private static void demorar(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void responderTexto(HttpExchange intercambio, String texto) throws IOException {
        byte[] bytes = texto.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().set("Content-Type", "text/plain;charset=UTF-8");
        intercambio.sendResponseHeaders(200, bytes.length);
        intercambio.getResponseBody().write(bytes);
    }

    private void responder(HttpExchange intercambio, int status, Object cuerpo) throws IOException {
        byte[] bytes = json.writeValueAsBytes(cuerpo);
        intercambio.getResponseHeaders().set("Content-Type", "application/json");
        intercambio.sendResponseHeaders(status, bytes.length);
        intercambio.getResponseBody().write(bytes);
    }

    @Override
    public void close() {
        servidor.stop(0);
        hilos.shutdownNow();
    }
}
