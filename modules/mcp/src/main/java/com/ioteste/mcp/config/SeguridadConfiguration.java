package com.ioteste.mcp.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Seguridad del servidor MCP.
 *
 * <ul>
 *   <li>CORS abierto para permitir clientes MCP basados en navegador (por ejemplo MCP Inspector).</li>
 *   <li>Si {@code MCP_API_KEY} está configurada, exige el header {@code X-API-Key} en todas las
 *       peticiones, con el mismo formato de error 401 que usa el engine.</li>
 * </ul>
 */
@Configuration
public class SeguridadConfiguration {

    static final String HEADER_API_KEY = "X-API-Key";

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.addExposedHeader("Mcp-Session-Id");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        FilterRegistrationBean<CorsFilter> registro = new FilterRegistrationBean<>(new CorsFilter(source));
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registro;
    }

    @Bean
    public FilterRegistrationBean<ApiKeyFilter> apiKeyFilter(@Value("${mcp.api.key:}") String apiKey) {
        FilterRegistrationBean<ApiKeyFilter> registro = new FilterRegistrationBean<>(new ApiKeyFilter(apiKey));
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registro;
    }

    /** Filtro de API key. Deja pasar todo si no hay clave configurada y los preflight CORS. */
    static class ApiKeyFilter extends OncePerRequestFilter {

        private final byte[] claveEsperada;

        ApiKeyFilter(String apiKey) {
            this.claveEsperada = apiKey == null || apiKey.isBlank()
                    ? null
                    : apiKey.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain chain) throws ServletException, IOException {

            if (claveEsperada == null || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
                chain.doFilter(request, response);
                return;
            }

            String recibida = request.getHeader(HEADER_API_KEY);
            if (recibida != null
                    && MessageDigest.isEqual(claveEsperada, recibida.getBytes(StandardCharsets.UTF_8))) {
                chain.doFilter(request, response);
                return;
            }

            String timestamp = Instant.now().atOffset(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"timestamp\": \"" + timestamp + "\", \"status\": 401, "
                    + "\"mensaje\": \"Falta o no coincide el header X-API-Key\", \"errores\": null}");
        }
    }
}

