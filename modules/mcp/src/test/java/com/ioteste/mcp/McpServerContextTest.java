package com.ioteste.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Levanta el contexto completo (sin base de datos real: Hikari conecta de forma diferida) y
 * verifica que las tools quedan publicadas en el servidor MCP y que el filtro de API key protege
 * los endpoints.
 */
@SpringBootTest(properties = {
    "mcp.api.key=secreto-test",
    "spring.datasource.url=jdbc:postgresql://localhost:1/inexistente"
})
@AutoConfigureMockMvc
class McpServerContextTest {

    @Autowired
    private List<ToolCallbackProvider> providers;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicaLasSieteToolsDeEcoWarm() {
        List<String> nombres = providers.stream()
                .flatMap(p -> Arrays.stream(p.getToolCallbacks()))
                .map(ToolCallback::getToolDefinition)
                .map(d -> d.name())
                .toList();

        assertThat(nombres).containsExactlyInAnyOrder(
                "obtenerSitio",
                "consultarEstadoHabitaciones",
                "historialTemperaturas",
                "obtenerEstadoControl",
                "iniciarControl",
                "detenerControl",
                "accionarSwitch");
    }

    @Test
    void lasToolsTienenDescripcionYEsquemaDeEntrada() {
        ToolCallback accionar = Arrays.stream(providers.get(0).getToolCallbacks())
                .filter(t -> t.getToolDefinition().name().equals("accionarSwitch"))
                .findFirst()
                .orElseThrow();

        assertThat(accionar.getToolDefinition().description()).contains("switch");
        assertThat(accionar.getToolDefinition().inputSchema()).contains("habitacionId", "encender");
    }

    @Test
    void sinApiKeyResponde401() throws Exception {
        mockMvc.perform(get("/mcp/sse"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}

