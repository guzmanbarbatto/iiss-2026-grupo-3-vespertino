package com.ioteste.mcp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Clientes HTTP hacia el engine y el simulador.
 *
 * <p>Ambos usan timeouts estrictos para que una caída de un servicio no deje colgada la tool
 * (y por lo tanto al agente de IA) esperando indefinidamente.
 */
@Configuration
public class HttpClientsConfiguration {

    private static final String HEADER_API_KEY = "X-API-Key";

    @Value("${mcp.http.connect-timeout-ms:2000}")
    private int connectTimeoutMs;

    @Value("${mcp.http.read-timeout-ms:5000}")
    private int readTimeoutMs;

    @Bean
    public RestClient engineRestClient(
            RestClient.Builder builder,
            @Value("${engine.url}") String engineUrl,
            @Value("${engine.api.key:}") String engineApiKey) {
        return construir(builder, engineUrl, engineApiKey);
    }

    @Bean
    public RestClient simuladorRestClient(
            RestClient.Builder builder,
            @Value("${simulador.url}") String simuladorUrl,
            @Value("${simulador.api.key:}") String simuladorApiKey) {
        return construir(builder, simuladorUrl, simuladorApiKey);
    }

    private RestClient construir(RestClient.Builder builder, String baseUrl, String apiKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);

        return builder.clone()
                .baseUrl(baseUrl)
                .defaultHeader(HEADER_API_KEY, apiKey)
                .requestFactory(factory)
                .build();
    }
}

