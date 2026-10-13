package com.ioteste.engine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ioteste.engine.repository.SitioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Set;

@Service
public class SitioService {

    private final SitioRepository repository;

    public SitioService(SitioRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public JsonNode obtenerSitio() {
        return repository.obtenerSitio();
    }

    @Transactional
    public void guardarSitio(JsonNode sitio) {
        validar(sitio);
        // Solo se persiste si toda la configuración es válida.
        repository.guardarSitio(sitio);
    }

    private void validar(JsonNode json) {
        if (json == null || !json.isObject()) {
            throw new IllegalArgumentException("El sitio debe ser un objeto JSON.");
        }

        String version = texto(json, "schemaVersion");

        if (version == null || !version.matches("^2\\.\\d+$")) {
            throw new IllegalArgumentException("schemaVersion debe pertenecer a la versión 2.");
        }

        JsonNode sitio = json.path("sitio");

        textoObligatorio(sitio, "id");
        textoObligatorio(sitio, "nombre");

        double potenciaContratada = numero(sitio, "potenciaContratadaKW");

        if (potenciaContratada <= 0) {
            throw new IllegalArgumentException("potenciaContratadaKW debe ser mayor que cero.");
        }

        JsonNode punta = sitio.path("tarifa").path("punta");

        if (punta.isMissingNode() || !punta.isObject()) {
            throw new IllegalArgumentException("Debe especificarse tarifa.punta.");
        }

        LocalTime desde = hora(texto(punta, "desde"), "desde");
        LocalTime hasta = hora(texto(punta, "hasta"), "hasta");

        if (!desde.isBefore(hasta)) {
            throw new IllegalArgumentException("La hora desde debe ser anterior a hasta.");
        }

        String dias = texto(punta, "dias");

        if (!"HABILES".equals(dias) && !"TODOS".equals(dias)) {
            throw new IllegalArgumentException("dias debe ser HABILES o TODOS.");
        }

        JsonNode habitaciones = json.path("habitaciones");

        if (!habitaciones.isArray()) {
            throw new IllegalArgumentException("habitaciones debe ser una lista.");
        }

        Set<String> ids = new HashSet<>();
        Set<String> termostatos = new HashSet<>();
        Set<String> switches = new HashSet<>();

        for (JsonNode habitacion : habitaciones) {
            String id = textoObligatorio(habitacion, "id");
            String nombre = textoObligatorio(habitacion, "nombre");
            String termostato = textoObligatorio(habitacion, "idTermostato");
            String idSwitch = textoObligatorio(habitacion, "idSwitch");

            textoObligatorio(habitacion, "topicTermostato");

            String url = textoObligatorio(habitacion, "urlSwitch");

            if (nombre.length() > 50) {
                throw new IllegalArgumentException("El nombre de la habitación no puede superar 50 caracteres.");
            }

            if (!ids.add(id)) {
                throw new IllegalArgumentException("Identificador de habitación duplicado: " + id);
            }

            if (!termostatos.add(termostato)) {
                throw new IllegalArgumentException("Identificador de termostato duplicado: " + termostato);
            }

            if (!switches.add(idSwitch)) {
                throw new IllegalArgumentException("Identificador de switch duplicado: " + idSwitch);
            }

            double potencia = numero(habitacion, "potenciaKW");

            if (potencia <= 0) {
                throw new IllegalArgumentException("potenciaKW debe ser mayor que cero.");
            }

            double temperatura = numero(habitacion, "temperaturaEsperada");

            if (Math.abs(temperatura * 10
                    - Math.round(temperatura * 10)) > 0.000001) {
                throw new IllegalArgumentException("temperaturaEsperada debe tener como máximo un decimal.");
            }

            try {
                URI uri = URI.create(url);

                if (!uri.isAbsolute()
                        || (!"http".equalsIgnoreCase(uri.getScheme())
                        && !"https".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null) {
                    throw new IllegalArgumentException("urlSwitch debe ser una URL HTTP o HTTPS válida.");
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("urlSwitch no es una URL válida.", e);
            }
        }
    }

    private String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo.path(campo);

        if (valor.isMissingNode() || valor.isNull()
                || !valor.isTextual()) {
            return null;
        }

        String texto = valor.asText().trim();
        return texto.isEmpty() ? null : texto;
    }

    private String textoObligatorio(JsonNode nodo, String campo) {
        String valor = texto(nodo, campo);

        if (valor == null) {
            throw new IllegalArgumentException("El campo " + campo + " es obligatorio.");
        }

        return valor;
    }

    private double numero(JsonNode nodo, String campo) {
        JsonNode valor = nodo.path(campo);

        if (!valor.isNumber() || !Double.isFinite(valor.asDouble())) {
            throw new IllegalArgumentException("El campo " + campo + " debe ser un número válido.");
        }

        return valor.asDouble();
    }

    private LocalTime hora(String valor, String campo) {
        if (valor == null) {
            throw new IllegalArgumentException("El campo " + campo + " es obligatorio.");
        }

        try {
            LocalTime hora = LocalTime.parse(valor);

            if (!valor.matches("\\d{2}:\\d{2}")) {
                throw new IllegalArgumentException("El campo " + campo + " debe tener formato HH:mm.");
            }

            return hora;
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("El campo " + campo + " debe ser una hora válida HH:mm.", e);
        }
    }
}
