package com.ioteste.engine.service;

import com.ioteste.engine.domain.Franja;
import com.ioteste.engine.domain.Habitacion;
import com.ioteste.engine.domain.InventarioDTO;
import com.ioteste.engine.domain.Sitio;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Reglas del inventario (§1) que no se pueden expresar en JSON Schema: campos obligatorios, rangos,
 * formato de la franja, {@code desde < hasta} y unicidad de {@code id}, {@code idTermostato} e
 * {@code idSwitch}. Devuelve todos los errores juntos para que quien carga el sitio los vea de una vez.
 */
@Component
public class ValidadorInventario {

    private static final Pattern SCHEMA_VERSION = Pattern.compile("^2\\.\\d+$");
    private static final Pattern HORA = Pattern.compile("^([01][0-9]|2[0-3]):[0-5][0-9]$");
    private static final Set<String> DIAS = Set.of("HABILES", "TODOS");
    private static final int MAX_NOMBRE_HABITACION = 50;

    public List<String> validar(InventarioDTO inventario) {
        List<String> errores = new ArrayList<>();
        if (inventario == null) {
            errores.add("el cuerpo es obligatorio");
            return errores;
        }
        String version = inventario.schemaVersion();
        if (version == null || !SCHEMA_VERSION.matcher(version).matches()) {
            errores.add("schemaVersion debe ser de la versión 2 (formato 2.N), es '" + version + "'");
        }
        validarSitio(inventario.sitio(), errores);
        if (inventario.habitaciones() == null) {
            errores.add("habitaciones es obligatorio");
        } else {
            validarHabitaciones(inventario.habitaciones(), errores);
        }
        return errores;
    }

    private static void validarSitio(Sitio sitio, List<String> errores) {
        if (sitio == null) {
            errores.add("sitio es obligatorio");
            return;
        }
        requerido(sitio.id(), "sitio.id", errores);
        requerido(sitio.nombre(), "sitio.nombre", errores);
        positivo(sitio.potenciaContratadaKW(), "sitio.potenciaContratadaKW", errores);
        if (sitio.tarifa() == null || sitio.tarifa().punta() == null) {
            errores.add("sitio.tarifa.punta es obligatoria");
            return;
        }
        Franja punta = sitio.tarifa().punta();
        boolean desdeValido = hora(punta.desde(), "desde", errores);
        boolean hastaValido = hora(punta.hasta(), "hasta", errores);
        if (desdeValido && hastaValido && punta.desde().compareTo(punta.hasta()) >= 0) {
            errores.add("tarifa.punta: desde (" + punta.desde() + ") debe ser anterior a hasta ("
                    + punta.hasta() + ")");
        }
        if (!DIAS.contains(punta.dias())) {
            errores.add("tarifa.punta.dias debe ser HABILES o TODOS, es '" + punta.dias() + "'");
        }
    }

    private static void validarHabitaciones(List<Habitacion> habitaciones, List<String> errores) {
        for (int i = 0; i < habitaciones.size(); i++) {
            Habitacion h = habitaciones.get(i);
            if (h == null) {
                errores.add("habitación #" + (i + 1) + ": no puede ser null");
                continue;
            }
            String nombre = "habitación '" + (vacio(h.id()) ? "#" + (i + 1) : h.id()) + "'";
            requerido(h.id(), nombre + ": id", errores);
            requerido(h.nombre(), nombre + ": nombre", errores);
            if (h.nombre() != null && h.nombre().length() > MAX_NOMBRE_HABITACION) {
                errores.add(nombre + ": nombre admite hasta " + MAX_NOMBRE_HABITACION + " caracteres");
            }
            if (h.temperaturaEsperada() == null) {
                errores.add(nombre + ": temperaturaEsperada es obligatoria");
            }
            positivo(h.potenciaKW(), nombre + ": potenciaKW", errores);
            requerido(h.idTermostato(), nombre + ": idTermostato", errores);
            if (requerido(h.topicTermostato(), nombre + ": topicTermostato", errores)
                    && (h.topicTermostato().contains("+") || h.topicTermostato().contains("#"))) {
                // El controlador se suscribe al topic declarado: un comodín lo suscribiría a otros dispositivos
                errores.add(nombre + ": topicTermostato no puede tener comodines MQTT (+ o #), es '"
                        + h.topicTermostato() + "'");
            }
            requerido(h.idSwitch(), nombre + ": idSwitch", errores);
            if (requerido(h.urlSwitch(), nombre + ": urlSwitch", errores) && !esUriAbsoluta(h.urlSwitch())) {
                errores.add(nombre + ": urlSwitch no es una URL válida ('" + h.urlSwitch() + "')");
            }
        }
        unicos(habitaciones, Habitacion::id, "id", errores);
        unicos(habitaciones, Habitacion::idTermostato, "idTermostato", errores);
        unicos(habitaciones, Habitacion::idSwitch, "idSwitch", errores);
    }

    private static void unicos(List<Habitacion> habitaciones, Function<Habitacion, String> campo, String nombre,
            List<String> errores) {
        Set<String> vistos = new HashSet<>();
        Set<String> duplicados = new HashSet<>();
        for (Habitacion h : habitaciones) {
            String valor = h == null ? null : campo.apply(h);
            if (!vacio(valor) && !vistos.add(valor) && duplicados.add(valor)) {
                errores.add(nombre + " duplicado: '" + valor + "'");
            }
        }
    }

    private static boolean requerido(String valor, String campo, List<String> errores) {
        if (vacio(valor)) {
            errores.add(campo + " es obligatorio");
            return false;
        }
        return true;
    }

    private static void positivo(Double valor, String campo, List<String> errores) {
        if (valor == null) {
            errores.add(campo + " es obligatorio");
        } else if (!(valor > 0) || valor.isInfinite()) {
            errores.add(campo + " debe ser > 0 (es " + valor + ")");
        }
    }

    private static boolean hora(String valor, String campo, List<String> errores) {
        if (valor == null || !HORA.matcher(valor).matches()) {
            errores.add("tarifa.punta." + campo + " debe tener formato HH:mm, es '" + valor + "'");
            return false;
        }
        return true;
    }

    private static boolean esUriAbsoluta(String valor) {
        try {
            return new URI(valor).isAbsolute();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
