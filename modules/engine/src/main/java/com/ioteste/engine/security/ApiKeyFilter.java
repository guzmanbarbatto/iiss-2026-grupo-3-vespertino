package com.ioteste.engine.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class ApiKeyFilter implements Filter {

    // Inyectamos la clave desde el application.properties
    @Value("${app.api.key}")
    private String expectedApiKey;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Extraemos el header tal como exige el estándar OpenAPI v2
        String apiKey = httpRequest.getHeader("X-API-Key");

        if (expectedApiKey != null && expectedApiKey.equals(apiKey)) {
            // La clave es correcta, dejamos pasar la petición a los controladores
            chain.doFilter(request, response);
        } else {
            // Falta la clave o es incorrecta: Devolvemos 401 con el JSON de error estándar
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType("application/json; charset=UTF-8");
            
            String timestamp = Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
            String jsonError = String.format(
                "{\"timestamp\": \"%s\", \"status\": 401, \"mensaje\": \"Falta o no coincide el header X-API-Key\", \"errores\": null}", 
                timestamp
            );
            
            httpResponse.getWriter().write(jsonError);
        }
    }
}