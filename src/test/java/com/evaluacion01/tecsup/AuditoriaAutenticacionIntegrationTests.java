package com.evaluacion01.tecsup;

import com.evaluacion01.tecsup.audit.ResultadoAuditoria;
import com.evaluacion01.tecsup.entity.AuditoriaLog;
import com.evaluacion01.tecsup.entity.Rol;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.AuditoriaLogRepository;
import com.evaluacion01.tecsup.repository.RolRepository;
import com.evaluacion01.tecsup.repository.UsuarioRepository;
import com.evaluacion01.tecsup.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditoriaAutenticacionIntegrationTests {

    @Autowired private MockMvc mvc;
    @Autowired private AuditoriaLogRepository logs;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private RolRepository roles;
    @Autowired private PasswordEncoder encoder;
    @Autowired private AuthService authService;

    private Usuario usuario;
    private Rol rol;
    private String hashInicial;

    @BeforeEach
    void preparar() {
        logs.deleteAll();
        rol = new Rol();
        rol.setNombre("Médico de auditoría");
        rol.setArea("Medicina");
        rol = roles.saveAndFlush(rol);
        usuario = new Usuario();
        usuario.setNombres("Prueba");
        usuario.setApellidos("Auditoría");
        usuario.setUsuario("authAudit");
        usuario.setCorreo("authAudit@pruebas.local");
        hashInicial = encoder.encode("claveValida");
        usuario.setContrasena(hashInicial);
        usuario.setRol(rol);
        usuario.setArea("Medicina");
        usuario = usuarios.saveAndFlush(usuario);
    }

    @AfterEach
    void limpiar() {
        usuarios.deleteById(usuario.getIdUsuario());
        roles.deleteById(rol.getIdRol());
        logs.deleteAll();
    }

    @Test
    void loginCorrectoPersisteUnSoloEventoYMetadatosHttp() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/login").with(csrf()).session(session)
                        .param("identificador", usuario.getUsuario()).param("contrasena", "claveValida")
                        .with(request -> { request.setRemoteAddr("192.0.2.7"); return request; }))
                .andExpect(redirectedUrl("/dashboard"));

        AuditoriaLog evento = unicoEvento();
        assertThat(evento.getAccion()).isEqualTo("LOGIN");
        assertThat(evento.getResultado()).isEqualTo(ResultadoAuditoria.EXITO);
        assertThat(evento.getIdUsuarioEjecutor()).isEqualTo(usuario.getIdUsuario());
        assertThat(evento.getUsuarioEjecutor()).isEqualTo(usuario.getUsuario());
        assertThat(evento.getIp()).isEqualTo("192.0.2.7");
        assertThat(evento.getRuta()).isEqualTo("/login");
        assertThat(evento.getMetodoHttp()).isEqualTo("POST");
        assertThat(evento.getDetalle()).doesNotContain("claveValida", hashInicial);
        assertThat(session.getAttribute("usuarioLogueado")).isInstanceOf(Usuario.class);
        assertThat(usuarios.findById(usuario.getIdUsuario()).orElseThrow().getUltimoAcceso()).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"authAudit", "authAudit@pruebas.local", "inexistente"})
    void conservaIntentosFallidosSinCrearSesionNiGuardarLaClave(String identificador) throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/login").with(csrf()).session(session)
                        .param("identificador", identificador).param("contrasena", "secretoRechazado"))
                .andExpect(status().isOk()).andExpect(view().name("login"))
                .andExpect(model().attributeExists("error"));

        AuditoriaLog evento = unicoEvento();
        assertThat(evento.getResultado()).isEqualTo(ResultadoAuditoria.FALLO);
        assertThat(evento.getDetalle()).isEqualTo("CREDENCIALES_INVALIDAS").doesNotContain("secretoRechazado");
        assertThat(evento.getUsuarioEjecutor()).isEqualTo("inexistente".equals(identificador) ? identificador : "authAudit");
        assertThat(session.getAttribute("usuarioLogueado")).isNull();
        assertThat(usuarios.findById(usuario.getIdUsuario()).orElseThrow().getContrasena()).isEqualTo(hashInicial);
    }

    @Test
    void unaCuentaInactivaGeneraUnIntentoDenegado() throws Exception {
        usuario.setEstado(false);
        usuarios.saveAndFlush(usuario);

        mvc.perform(post("/login").with(csrf()).param("identificador", usuario.getUsuario())
                        .param("contrasena", "claveValida"))
                .andExpect(status().isOk()).andExpect(model().attributeExists("error"));

        AuditoriaLog evento = unicoEvento();
        assertThat(evento.getResultado()).isEqualTo(ResultadoAuditoria.DENEGADO);
        assertThat(evento.getDetalle()).isEqualTo("CUENTA_INACTIVA");
        assertThat(usuarios.findById(usuario.getIdUsuario()).orElseThrow().getUltimoAcceso()).isNull();
    }

    @Test
    void datosIncompletosTambienSeRegistranComoIntentoFallido() throws Exception {
        mvc.perform(post("/login").with(csrf()).param("identificador", "").param("contrasena", ""))
                .andExpect(status().isOk()).andExpect(model().attributeExists("error"));

        AuditoriaLog evento = unicoEvento();
        assertThat(evento.getUsuarioEjecutor()).isEqualTo("ANONIMO");
        assertThat(evento.getDetalle()).isEqualTo("DATOS_INCOMPLETOS");
        assertThat(evento.getResultado()).isEqualTo(ResultadoAuditoria.FALLO);
    }

    @Test
    void logoutRegistraAlUsuarioAntesDeInvalidarSuSesion() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogueado", usuario);

        mvc.perform(get("/logout").session(session)).andExpect(redirectedUrl("/login"));

        AuditoriaLog evento = unicoEvento();
        assertThat(evento.getAccion()).isEqualTo("LOGOUT");
        assertThat(evento.getResultado()).isEqualTo(ResultadoAuditoria.EXITO);
        assertThat(evento.getIdUsuarioEjecutor()).isEqualTo(usuario.getIdUsuario());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void logoutAnonimoNoGeneraUnCierreFicticio() throws Exception {
        mvc.perform(get("/logout")).andExpect(redirectedUrl("/login"));
        assertThat(logs.count()).isZero();
    }

    @Test
    void noRegistraUnLoginExitosoSiElFormularioNoTieneCsrf() throws Exception {
        mvc.perform(post("/login").param("identificador", usuario.getUsuario()).param("contrasena", "claveValida"))
                .andExpect(status().isForbidden());
        assertThat(logs.count()).isZero();
    }

    @Test
    void elServicioTambienAuditaAutenticacionesFueraDeHttp() throws Exception {
        authService.autenticar(usuario.getUsuario(), "claveValida");

        AuditoriaLog evento = unicoEvento();
        assertThat(evento.getResultado()).isEqualTo(ResultadoAuditoria.EXITO);
        assertThat(evento.getRuta()).isNull();
        assertThat(evento.getIp()).isNull();
    }

    private AuditoriaLog unicoEvento() {
        assertThat(logs.findAll()).hasSize(1);
        return logs.findAll().get(0);
    }
}
