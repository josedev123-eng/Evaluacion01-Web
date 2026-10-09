package com.evaluacion01.tecsup.config;

import com.evaluacion01.tecsup.audit.AuditoriaContext;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditoriaContextFilterTest {

    private final AuditoriaContextFilter filter = new AuditoriaContextFilter();

    @AfterEach
    void limpiar() {
        AuditoriaContext.limpiar();
    }

    @Test
    void capturaIpDirectaSinQueryNiCabecerasFalsificadasYLimpiaDespues() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/reset-password");
        request.setRemoteAddr("192.0.2.1");
        request.setQueryString("token=secreto");
        request.addHeader("X-Forwarded-For", "203.0.113.9");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            assertThat(AuditoriaContext.obtener().ip()).isEqualTo("192.0.2.1");
            assertThat(AuditoriaContext.obtener().metodo()).isEqualTo("POST");
            assertThat(AuditoriaContext.obtener().ruta()).isEqualTo("/reset-password");
        });

        assertThat(AuditoriaContext.obtener().ip()).isNull();
        assertThat(AuditoriaContext.obtener().ruta()).isNull();
    }

    @Test
    void limpiaAunqueLaCadenaLanceUnaExcepcion() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/usuarios");

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            throw new ServletException("Error de prueba");
        })).isInstanceOf(ServletException.class);

        assertThat(AuditoriaContext.obtener().ip()).isNull();
    }
}
