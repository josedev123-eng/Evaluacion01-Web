package com.evaluacion01.tecsup.config;

import com.evaluacion01.tecsup.audit.ModuloAuditoria;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.service.AuditoriaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class AuditoriaAccesoDenegadoHandler implements AccessDeniedHandler {

    private final AuditoriaService auditoriaService;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        HttpSession session = request.getSession(false);
        Usuario operador = session != null && session.getAttribute("usuarioLogueado") instanceof Usuario usuario
                ? usuario : null;
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        boolean csrf = exception instanceof CsrfException;
        auditoriaService.registrarFallo(operador, null, ModuloAuditoria.deRuta(ruta),
                csrf ? "CSRF_RECHAZADO" : "ACCESO_DENEGADO", null, null,
                csrf ? "CSRF_INVALIDO" : "SIN_PERMISO", true);
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
    }
}
