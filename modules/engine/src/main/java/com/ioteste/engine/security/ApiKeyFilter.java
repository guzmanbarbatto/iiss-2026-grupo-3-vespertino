package com.ioteste.engine.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ioteste.engine.domain.ErrorRespuesta;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Exige el header {@code X-API-Key} con la clave del controlador en todas las peticiones. */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private final byte[] claveEsperada;
    private final ObjectMapper objectMapper;

    public ApiKeyFilter(@Value("${app.api.key}") String apiKey, ObjectMapper objectMapper) {
        this.claveEsperada = apiKey.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String recibida = request.getHeader("X-API-Key");
        // Comparación en tiempo constante: no revela cuántos caracteres de la clave coinciden
        if (recibida != null && MessageDigest.isEqual(claveEsperada, recibida.getBytes(StandardCharsets.UTF_8))) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
                ErrorRespuesta.de(401, "falta o no coincide el header X-API-Key"));
    }
}
