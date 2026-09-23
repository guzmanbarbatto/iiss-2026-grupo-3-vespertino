package com.ioteste.api.Security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class ApiKeyFilter implements Filter {

    // Extrae el token esperado o usa el que definiste en tu script de pruebas por defecto
    @Value("${api.auth.token:token_secreto_desarrollo_123}")
    private String tokenEsperado;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
            
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String headerAutenticacion = req.getHeader("Authorization");

        if (headerAutenticacion != null && headerAutenticacion.startsWith("Bearer ")) {
            String tokenIngresado = headerAutenticacion.substring(7);
            
            if (tokenEsperado.equals(tokenIngresado)) {
                // El token es correcto, permite que la petición continúe hacia los Controladores
                chain.doFilter(request, response);
                return;
            }
        }

        // Si no hay token o es incorrecto, bloquea el acceso
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.getWriter().write("Acceso denegado: Token Bearer ausente o invalido.");
    }
}