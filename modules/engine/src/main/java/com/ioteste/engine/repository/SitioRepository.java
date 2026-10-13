package com.ioteste.engine.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Repository
public class SitioRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public SitioRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public JsonNode obtenerSitio() {
        List<Map<String, Object>> sitios = jdbc.queryForList("""
                SELECT id, nombre, potencia_contratada_kw, schema_version
                FROM sitio_configuracion
                WHERE clave = 1
                """);

        if (sitios.isEmpty()) {
            return null;
        }

        Map<String, Object> sitioDB = sitios.get(0);

        List<Map<String, Object>> tarifas = jdbc.queryForList("""
                SELECT desde, hasta, dias
                FROM tarifa_punta
                WHERE sitio_clave = 1
                """);

        if (tarifas.isEmpty()) {
            throw new IllegalStateException("La configuración del sitio no tiene tarifa punta.");
        }

        Map<String, Object> tarifaDB = tarifas.get(0);

        ObjectNode respuesta = mapper.createObjectNode();
        respuesta.put("schemaVersion", (String) sitioDB.get("schema_version"));

        ObjectNode sitio = respuesta.putObject("sitio");
        sitio.put("id", (String) sitioDB.get("id"));
        sitio.put("nombre", (String) sitioDB.get("nombre"));
        sitio.put("potenciaContratadaKW", ((Number) sitioDB.get("potencia_contratada_kw")).doubleValue());

        ObjectNode tarifa = sitio.putObject("tarifa");
        ObjectNode punta = tarifa.putObject("punta");
        punta.put("desde", ((Time) tarifaDB.get("desde")).toLocalTime().toString());
        punta.put("hasta", ((Time) tarifaDB.get("hasta")).toLocalTime().toString());
        punta.put("dias", (String) tarifaDB.get("dias"));

        ArrayNode habitaciones = respuesta.putArray("habitaciones");

        jdbc.query("""
                SELECT id, nombre, temperatura_esperada, potencia_kw,
                termostato_id, topic_termostato, switch_id, url_switch
                FROM sitio_habitacion
                WHERE sitio_clave = 1
                ORDER BY id
                """, resultado -> {
            ObjectNode h = habitaciones.addObject();
            h.put("id", resultado.getString("id"));
            h.put("nombre", resultado.getString("nombre"));
            h.put("temperaturaEsperada", resultado.getBigDecimal("temperatura_esperada").doubleValue());
            h.put("potenciaKW", resultado.getBigDecimal("potencia_kw").doubleValue());
            h.put("idTermostato", resultado.getString("termostato_id"));
            h.put("topicTermostato", resultado.getString("topic_termostato"));
            h.put("idSwitch", resultado.getString("switch_id"));
            h.put("urlSwitch", resultado.getString("url_switch"));
        });

        return respuesta;
    }

    public void guardarSitio(JsonNode json) {
        JsonNode sitio = json.path("sitio");
        JsonNode punta = sitio.path("tarifa").path("punta");

        jdbc.update("""
                INSERT INTO sitio_configuracion
                    (clave, id, nombre, potencia_contratada_kw,
                     schema_version)
                VALUES (1, ?, ?, ?, ?)
                ON CONFLICT (clave) DO UPDATE SET
                    id = EXCLUDED.id,
                    nombre = EXCLUDED.nombre,
                    potencia_contratada_kw =
                        EXCLUDED.potencia_contratada_kw,
                    schema_version = EXCLUDED.schema_version
                """,
                sitio.path("id").asText(),
                sitio.path("nombre").asText(),
                sitio.path("potenciaContratadaKW").decimalValue(),
                json.path("schemaVersion").asText());

        jdbc.update("""
                INSERT INTO tarifa_punta
                    (sitio_clave, desde, hasta, dias)
                VALUES (1, ?, ?, ?)
                ON CONFLICT (sitio_clave) DO UPDATE SET
                    desde = EXCLUDED.desde,
                    hasta = EXCLUDED.hasta,
                    dias = EXCLUDED.dias
                """,
                Time.valueOf(LocalTime.parse(punta.path("desde").asText())),
                Time.valueOf(LocalTime.parse(punta.path("hasta").asText())),
                punta.path("dias").asText());

        // Se reemplazan todas las habitaciones de la configuración.
        jdbc.update("DELETE FROM sitio_habitacion WHERE sitio_clave = 1");

        for (JsonNode h : json.path("habitaciones")) {
            jdbc.update("""
                    INSERT INTO sitio_habitacion
                        (id, sitio_clave, nombre, temperatura_esperada,
                         potencia_kw, termostato_id, topic_termostato,
                         switch_id, url_switch)
                    VALUES (?, 1, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    h.path("id").asText(),
                    h.path("nombre").asText(),
                    h.path("temperaturaEsperada").decimalValue(),
                    h.path("potenciaKW").decimalValue(),
                    h.path("idTermostato").asText(),
                    h.path("topicTermostato").asText(),
                    h.path("idSwitch").asText(),
                    h.path("urlSwitch").asText());
        }
    }
}

