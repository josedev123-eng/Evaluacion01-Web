package com.evaluacion01.tecsup.config;

import com.evaluacion01.tecsup.audit.AuditoriaContext;
import com.evaluacion01.tecsup.entity.Usuario;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

// Captura en cada petición el usuario logueado y la IP para la auditoría.
// No altera el flujo de ninguna ruta: solo alimenta AuditoriaContext.
@Component
public class AuditoriaInterceptor implements HandlerInterceptor {

    private static final String ATRIBUTO_USUARIO = "usuarioLogueado";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            HttpSession session = request.getSession(false);
            String usuario = null;
            if (session != null) {
                Object logueado = session.getAttribute(ATRIBUTO_USUARIO);
                if (logueado instanceof Usuario u) {
                    usuario = u.getUsuario();
                }
            }
            AuditoriaContext.set(usuario, obtenerIp(request));
        } catch (Exception e) {
            AuditoriaContext.clear();
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuditoriaContext.clear();
    }

    private String obtenerIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            return ip.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
