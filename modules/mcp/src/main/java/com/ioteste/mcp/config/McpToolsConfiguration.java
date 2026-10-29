package com.ioteste.mcp.config;

import com.ioteste.mcp.tools.EcoWarmTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra las tools de EcoWarm en el servidor MCP.
 *
 * <p>En Spring AI 1.0 el autoconfigurador del servidor MCP publica como tools todos los beans
 * {@link ToolCallbackProvider}. Los métodos anotados con {@code @Tool} de {@link EcoWarmTools} se
 * convierten en tools con su esquema JSON generado automáticamente.
 */
@Configuration
public class McpToolsConfiguration {

    @Bean
    public ToolCallbackProvider ecoWarmToolCallbacks(EcoWarmTools ecoWarmTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(ecoWarmTools)
                .build();
    }
}
