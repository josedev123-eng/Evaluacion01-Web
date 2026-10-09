package com.evaluacion01.tecsup.config;

import com.evaluacion01.tecsup.audit.AuditoriaContext;
import com.evaluacion01.tecsup.entity.Usuario;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;

class AuditoriaInterceptorTest {

    private final AuditoriaInterceptor interceptor = new AuditoriaInterceptor();

    @AfterEach
    void limpiarContexto() {
        AuditoriaContext.clear();
    }

    private Usuario usuario(String nombre) {
        Usuario usuario = new Usuario();
        usuario.setUsuario(nombre);
        return usuario;
    }

    @Test
    void debeCapturarElUsuarioDeLaSesionYLaIp() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogueado", usuario("jdoe"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        request.setRemoteAddr("192.168.1.20");
        HttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(AuditoriaContext.getUsuario()).isEqualTo("jdoe");
        assertThat(AuditoriaContext.getIp()).isEqualTo("192.168.1.20");
    }

    @Test
    void debeTomarLaIpDelHeaderCuandoExisteProxy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");
        HttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        assertThat(AuditoriaContext.getIp()).isEqualTo("203.0.113.5");
    }

    @Test
    void debeUsarSistemaCuandoNoHaySesionIniciada() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        HttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        assertThat(AuditoriaContext.getUsuario()).isEqualTo("sistema");
    }

    @Test
    void debeLiberarElContextoAlTerminarLaPeticion() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogueado", usuario("jdoe"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        HttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(AuditoriaContext.getIp()).isNull();
        assertThat(AuditoriaContext.getUsuario()).isEqualTo("sistema");
    }

    @Test
    void nuncaDebeImpedirElPasoDeLaPeticion() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());
        HttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }
}
