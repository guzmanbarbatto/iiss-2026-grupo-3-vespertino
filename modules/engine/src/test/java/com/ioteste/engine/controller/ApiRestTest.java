package com.ioteste.engine.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ioteste.engine.domain.EstadoControl;
import com.ioteste.engine.domain.InventarioDTO;
import com.ioteste.engine.domain.InventarioReemplazado;
import com.ioteste.engine.repository.SitioRepository;
import com.ioteste.engine.service.EvaluadorJobService;
import com.ioteste.engine.service.ValidadorInventario;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** API REST del controlador (§5) contra su contrato, con el filtro de API key incluido. */
@WebMvcTest(controllers = {SitioController.class, ControlController.class})
@Import(ValidadorInventario.class)
@TestPropertySource(properties = "app.api.key=clave-test")
@RecordApplicationEvents
class ApiRestTest {

    private static final String INVENTARIO = """
            {"schemaVersion":"2.0",
             "sitio":{"id":"casa-rodriguez","nombre":"Casa Rodríguez","potenciaContratadaKW":3.7,
                      "tarifa":{"punta":{"desde":"17:00","hasta":"23:00","dias":"HABILES"}}},
             "habitaciones":[
               {"id":"room1","nombre":"Living","temperaturaEsperada":21.5,"potenciaKW":1.2,
                "idTermostato":"ht-sim-room1","topicTermostato":"ht-sim-room1/status/temperature:0",
                "idSwitch":"sw-sim-room1","urlSwitch":"http://iiss-simulator:9090"},
               {"id":"room2","nombre":"Dormitorio principal","temperaturaEsperada":20.0,"potenciaKW":0.8,
                "idTermostato":"ht-sim-room2","topicTermostato":"ht-sim-room2/status/temperature:0",
                "idSwitch":"sw-sim-room2","urlSwitch":"http://iiss-simulator:9090"}]}
            """;

    private static final EstadoControl EN_EJECUCION =
            new EstadoControl("EN_EJECUCION", "2026-10-01T09:15:03.120Z", 10.0, "2026-11-03T15:00:00.000Z");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ApplicationEvents eventos;
    @MockitoBean private SitioRepository repository;
    @MockitoBean private EvaluadorJobService motor;

    // ---------------------------------------------------------------- seguridad

    @Test
    void sinApiKeyResponde401ConElCuerpoDeErrorDelContrato() throws Exception {
        mvc.perform(get("/sitio"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.mensaje").value("falta o no coincide el header X-API-Key"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.errores").isEmpty());
        verifyNoInteractions(repository);
    }

    @Test
    void conApiKeyIncorrectaResponde401() throws Exception {
        mvc.perform(get("/control").header("X-API-Key", "clave-test-mal")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- /sitio

    @Test
    void getSitioSinCargarResponde404ConCuerpoDeError() throws Exception {
        when(repository.obtenerInventario()).thenReturn(Optional.empty());

        mvc.perform(conClave(get("/sitio")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getSitioDevuelveLoPersistido() throws Exception {
        when(repository.obtenerInventario()).thenReturn(Optional.of(
                objectMapper.readValue(INVENTARIO, InventarioDTO.class)));

        mvc.perform(conClave(get("/sitio")))
                .andExpect(status().isOk())
                .andExpect(content().json(INVENTARIO, JsonCompareMode.STRICT));
    }

    @Test
    void putSitioValidoSePersisteYResponde200() throws Exception {
        mvc.perform(conClave(put("/sitio")).contentType(MediaType.APPLICATION_JSON).content(INVENTARIO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sitio").value("casa-rodriguez"))
                .andExpect(jsonPath("$.habitaciones").value(2));
        verify(repository).guardarInventario(any(InventarioDTO.class));
    }

    @Test
    void putSitioAvisaQueElInventarioCambioConElAnteriorYElNuevo() throws Exception {
        InventarioDTO anterior = objectMapper.readValue(INVENTARIO, InventarioDTO.class);
        when(repository.obtenerInventario()).thenReturn(Optional.of(anterior));

        mvc.perform(conClave(put("/sitio")).contentType(MediaType.APPLICATION_JSON).content(INVENTARIO))
                .andExpect(status().isOk());

        InOrder orden = inOrder(repository);
        orden.verify(repository).obtenerInventario();
        orden.verify(repository).guardarInventario(any(InventarioDTO.class));
        assertThat(eventos.stream(InventarioReemplazado.class)).singleElement().satisfies(evento -> {
            assertThat(evento.anterior()).contains(anterior);
            assertThat(evento.nuevo().sitio().id()).isEqualTo("casa-rodriguez");
        });
    }

    @Test
    void putSitioInvalidoResponde400ConTodosLosErroresYNoTocaLaBase() throws Exception {
        String invalido = INVENTARIO.replace("\"desde\":\"17:00\"", "\"desde\":\"25:00\"")
                .replace("\"idSwitch\":\"sw-sim-room2\"", "\"idSwitch\":\"sw-sim-room1\"");

        mvc.perform(conClave(put("/sitio")).contentType(MediaType.APPLICATION_JSON).content(invalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensaje").value("inventario inválido"))
                .andExpect(jsonPath("$.errores[0]").value("tarifa.punta.desde debe tener formato HH:mm, es '25:00'"))
                .andExpect(jsonPath("$.errores[1]").value("idSwitch duplicado: 'sw-sim-room1'"));
        verify(repository, never()).guardarInventario(any());
        assertThat(eventos.stream(InventarioReemplazado.class)).isEmpty();
    }

    @Test
    void putSitioConSitioNuloResponde400YNo500() throws Exception {
        mvc.perform(conClave(put("/sitio")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schemaVersion\":\"2.0\",\"sitio\":null,\"habitaciones\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0]").value("sitio es obligatorio"));
        verify(repository, never()).guardarInventario(any());
    }

    @Test
    void putSitioConJsonMalformadoResponde400() throws Exception {
        mvc.perform(conClave(put("/sitio")).contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void unDatoQueLaBaseRechazaEs400() throws Exception {
        doThrow(new DataIntegrityViolationException("valor demasiado largo"))
                .when(repository).guardarInventario(any());

        mvc.perform(conClave(put("/sitio")).contentType(MediaType.APPLICATION_JSON).content(INVENTARIO))
                .andExpect(status().isBadRequest());
    }

    @Test
    void conLaBaseCaidaResponde503() throws Exception {
        when(repository.obtenerInventario()).thenThrow(new CannotGetJdbcConnectionException("sin conexión"));

        mvc.perform(conClave(get("/sitio")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    // ---------------------------------------------------------------- /control

    @Test
    void getControlDevuelveElEstadoDelMotor() throws Exception {
        when(motor.estadoControl()).thenReturn(new EstadoControl("DETENIDO", null, null, null));

        mvc.perform(conClave(get("/control")))
                .andExpect(status().isOk())
                .andExpect(content().json(
                        "{\"estado\":\"DETENIDO\",\"fechaHora\":null,\"factorTiempo\":null,\"iniciadoEn\":null}",
                        JsonCompareMode.STRICT));
    }

    @Test
    void iniciarSincronizaElMotorConLaFechaYElFactor() throws Exception {
        when(motor.iniciarMotor(Instant.parse("2026-10-01T09:15:03.120Z"), 10.0)).thenReturn(EN_EJECUCION);

        comando("{\"accion\":\"INICIAR\",\"fechaHora\":\"2026-10-01T06:15:03.120-03:00\",\"factorTiempo\":10}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_EJECUCION"));
    }

    @Test
    void iniciarSinFactorUsaUno() throws Exception {
        when(motor.iniciarMotor(Instant.parse("2026-10-01T09:15:03.120Z"), 1.0)).thenReturn(EN_EJECUCION);

        comando("{\"accion\":\"INICIAR\",\"fechaHora\":\"2026-10-01T09:15:03.120Z\"}").andExpect(status().isOk());
        verify(motor).iniciarMotor(Instant.parse("2026-10-01T09:15:03.120Z"), 1.0);
    }

    @Test
    void comandosInvalidosResponden400SinTocarElMotor() throws Exception {
        comando("{\"accion\":\"INICIAR\"}").andExpect(status().isBadRequest());
        comando("{\"accion\":\"INICIAR\",\"fechaHora\":\"ayer\"}").andExpect(status().isBadRequest());
        comando("{\"accion\":\"INICIAR\",\"fechaHora\":\"2026-10-01T09:15:00Z\",\"factorTiempo\":0}")
                .andExpect(status().isBadRequest());
        comando("{\"accion\":\"INICIAR\",\"fechaHora\":\"2026-10-01T09:15:00Z\",\"factorTiempo\":-2}")
                .andExpect(status().isBadRequest());
        comando("{\"accion\":\"INICIAR\",\"fechaHora\":\"2026-10-01T09:15:00Z\",\"factorTiempo\":\"x\"}")
                .andExpect(status().isBadRequest());
        comando("{\"accion\":\"PAUSAR\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("accion desconocida 'PAUSAR': debe ser INICIAR o DETENER"));
        comando("{}").andExpect(status().isBadRequest());

        verify(motor, never()).iniciarMotor(any(), anyDouble());
    }

    @Test
    void detenerDelegaEnElMotor() throws Exception {
        when(motor.detenerMotor()).thenReturn(new EstadoControl("DETENIDO", null, null, null));

        comando("{\"accion\":\"DETENER\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DETENIDO"));
    }

    // ---------------------------------------------------------------- auxiliares

    private static MockHttpServletRequestBuilder conClave(MockHttpServletRequestBuilder pedido) {
        return pedido.header("X-API-Key", "clave-test");
    }

    private ResultActions comando(String cuerpo) throws Exception {
        return mvc.perform(conClave(post("/control/comandos")).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }
}
