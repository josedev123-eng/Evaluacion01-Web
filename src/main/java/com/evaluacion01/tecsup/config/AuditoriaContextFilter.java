package com.evaluacion01.tecsup.config;

import com.evaluacion01.tecsup.audit.AuditoriaContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuditoriaContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // No confiar en X-Forwarded-For sin una configuración explícita de proxies de confianza.
        AuditoriaContext.establecer(new AuditoriaContext.DatosPeticion(
                request.getRemoteAddr(), request.getMethod(), request.getRequestURI()));
        try {
            chain.doFilter(request, response);
        } finally {
            AuditoriaContext.limpiar();
        }
    }
}
