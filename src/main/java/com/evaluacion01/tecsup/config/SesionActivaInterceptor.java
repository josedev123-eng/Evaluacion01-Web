package com.evaluacion01.tecsup.config;

import com.evaluacion01.tecsup.audit.ModuloAuditoria;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.UsuarioRepository;
import com.evaluacion01.tecsup.service.AuditoriaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

// RF-USR-03: antes, desactivar a un usuario solo le impedía volver a iniciar sesión;
// si ya estaba dentro seguía navegando con su sesión abierta. Este interceptor recarga
// al usuario de la sesión en cada petición: si fue desactivado (o eliminado) cierra su
// sesión, y si no, refresca sus datos (por ejemplo, roles cambiados por el administrador).
@Component
@RequiredArgsConstructor
public class SesionActivaInterceptor implements HandlerInterceptor {

    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("usuarioLogueado") instanceof Usuario enSesion)) {
            if (esRutaProtegida(request)) {
                String ruta = request.getRequestURI().substring(request.getContextPath().length());
                auditoriaService.registrarFallo(null, null, ModuloAuditoria.deRuta(ruta), "ACCESO_DENEGADO",
                        null, null, "SIN_SESION", true);
                response.sendRedirect(request.getContextPath() + "/login");
                return false;
            }
            return true;
        }
        Usuario actual = enSesion.getIdUsuario() == null ? null
                : usuarioRepository.findById(enSesion.getIdUsuario()).orElse(null);
        if (actual == null || !Boolean.TRUE.equals(actual.getEstado())) {
            auditoriaService.registrarFallo(enSesion, null, ModuloAuditoria.AUTENTICACION, "SESION_INVALIDA",
                    "Usuario", enSesion.getIdUsuario(), "SESION_INACTIVA_O_ELIMINADA", true);
            session.invalidate();
            response.sendRedirect(request.getContextPath() + "/login?cuentaInactiva");
            return false;
        }
        session.setAttribute("usuarioLogueado", actual);
        return true;
    }

    private boolean esRutaProtegida(HttpServletRequest request) {
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        return "/dashboard".equals(ruta) || "/usuarios".equals(ruta) || ruta.startsWith("/usuarios/")
                || "/roles".equals(ruta) || ruta.startsWith("/roles/");
    }
}
