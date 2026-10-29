package com.ioteste.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del servidor MCP de EcoWarm.
 *
 * <p>Expone, mediante el Model Context Protocol (transporte HTTP + SSE), un conjunto de tools para
 * que un agente de IA pueda consultar y operar el controlador de calefacción.
 */
@SpringBootApplication
public class EcoWarmMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcoWarmMcpApplication.class, args);
    }
}
