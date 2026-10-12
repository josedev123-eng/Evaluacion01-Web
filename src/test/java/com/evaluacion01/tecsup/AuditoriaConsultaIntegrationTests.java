package com.evaluacion01.tecsup;

import com.evaluacion01.tecsup.audit.ModuloAuditoria;
import com.evaluacion01.tecsup.audit.ResultadoAuditoria;
import com.evaluacion01.tecsup.dto.AuditoriaFiltroDto;
import com.evaluacion01.tecsup.entity.*;
import com.evaluacion01.tecsup.repository.*;
import com.evaluacion01.tecsup.service.AuditoriaConsultaService;
import com.evaluacion01.tecsup.service.AutorizacionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditoriaConsultaIntegrationTests {

    @Autowired private MockMvc mvc;
    @Autowired private AuditoriaLogRepository logs;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private RolRepository roles;
    @Autowired private PermisoRepository permisos;
    @Autowired private AuditoriaConsultaService consulta;
    @Autowired private AutorizacionService autorizacion;
    @Autowired private PasswordEncoder encoder;

    private Rol administrador;
    private Rol lector;
    private Usuario admin;
    private Usuario adminAdicional;
    private Usuario noAdmin;
    private AuditoriaLog primero;
    private AuditoriaLog segundo;
    private AuditoriaLog tercero;
    private AuditoriaLog cuarto;
    private AuditoriaLog quinto;

    @BeforeEach
    void preparar() {
        logs.deleteAll();
        administrador = rol("Administrador");
        lector = rol("Lector de consulta");
        Permiso supuestoAcceso = new Permiso();
        supuestoAcceso.setModulo("auditoria");
        supuestoAcceso.setNombre("VER");
        supuestoAcceso = permisos.saveAndFlush(supuestoAcceso);
        lector.setPermisos(new LinkedHashSet<>(Set.of(supuestoAcceso)));
        lector = roles.saveAndFlush(lector);
        admin = usuario("adminConsulta", administrador);
        noAdmin = usuario("lectorConsulta", lector);
        adminAdicional = usuario("adminAdicionalConsulta", lector);
        adminAdicional.getRoles().add(administrador);
        adminAdicional = usuarios.saveAndFlush(adminAdicional);

        primero = evento("2026-10-01T00:00:00", "leonel-m4", ModuloAuditoria.USUARIOS, ResultadoAuditoria.EXITO,
                "<script>alert('xss')</script>");
        segundo = evento("2026-10-02T23:59:59", "leonel-m4", ModuloAuditoria.USUARIOS, ResultadoAuditoria.FALLO, "Rechazo");
        tercero = evento("2026-10-03T00:00:00", "edu", ModuloAuditoria.ROLES, ResultadoAuditoria.EXITO, "Rol");
        cuarto = evento("2026-10-03T00:00:00", "karimSOVC", ModuloAuditoria.PERMISOS, ResultadoAuditoria.DENEGADO, "Permisos");
        quinto = evento("2026-10-04T12:00:00", "literal_%", ModuloAuditoria.AUTENTICACION, ResultadoAuditoria.EXITO, "Login");
    }

    @AfterEach
    void limpiarDatosH2() {
        usuarios.deleteAll();
        roles.deleteAll();
        permisos.deleteAll();
        logs.deleteAll();
    }

    @Test
    void administradorConsultaConOrdenEstableYTamanioLimitado() throws Exception {
        Page<AuditoriaLog> pagina = pagina(get("/auditoria"));

        assertThat(pagina.getSize()).isEqualTo(25);
        assertThat(pagina.getTotalElements()).isEqualTo(5);
        assertThat(pagina.getContent()).extracting(AuditoriaLog::getIdAuditoria)
                .containsExactly(quinto.getIdAuditoria(), cuarto.getIdAuditoria(), tercero.getIdAuditoria(),
                        segundo.getIdAuditoria(), primero.getIdAuditoria());
    }

    @Test
    void administradorAdicionalTambienTieneAcceso() throws Exception {
        mvc.perform(get("/auditoria").session(sesion(adminAdicional)))
                .andExpect(status().isOk()).andExpect(view().name("auditoria/lista"));
    }

    @Test
    void noAdministradorNoAccedeAunqueSuRolTengaAuditoriaVer() throws Exception {
        assertThat(autorizacion.tienePermiso(noAdmin, "auditoria", "VER")).isFalse();
        mvc.perform(get("/auditoria").session(sesion(noAdmin)))
                .andExpect(redirectedUrl("/dashboard"))
                .andExpect(flash().attributeExists("error"))
                .andExpect(model().attributeDoesNotExist("paginaAuditoria"));

        assertThat(logs.findAll()).hasSize(6).anySatisfy(evento -> {
            assertThat(evento.getAccion()).isEqualTo("CONSULTAR_AUDITORIA");
            assertThat(evento.getResultado()).isEqualTo(ResultadoAuditoria.DENEGADO);
        });
    }

    @Test
    void sinSesionNoEntregaDatosYRegistraElIntento() throws Exception {
        MvcResult result = mvc.perform(get("/auditoria")).andExpect(redirectedUrl("/login")).andReturn();
        assertThat(result.getModelAndView()).isNull();
        assertThat(result.getResponse().getContentAsString()).isEmpty();
        assertThat(logs.findAll()).hasSize(6).anySatisfy(evento -> {
            assertThat(evento.getModulo()).isEqualTo(ModuloAuditoria.AUDITORIA);
            assertThat(evento.getAccion()).isEqualTo("ACCESO_DENEGADO");
        });
    }

    @Test
    void desactivarAdministradorInvalidaSuSesionYBloqueaLaConsulta() throws Exception {
        MockHttpSession session = sesion(admin);
        admin.setEstado(false);
        usuarios.saveAndFlush(admin);

        mvc.perform(get("/auditoria").session(session)).andExpect(redirectedUrl("/login?cuentaInactiva"));
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void revocarElRolAdicionalBloqueaUnaSesionAnterior() throws Exception {
        MockHttpSession session = sesion(adminAdicional);
        adminAdicional.setRoles(new LinkedHashSet<>(Set.of(lector)));
        usuarios.saveAndFlush(adminAdicional);

        mvc.perform(get("/auditoria").session(session)).andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void filtraPorModulo() throws Exception {
        Page<AuditoriaLog> pagina = pagina(get("/auditoria").param("modulo", "USUARIOS"));
        assertThat(pagina.getContent()).extracting(AuditoriaLog::getIdAuditoria)
                .containsExactly(segundo.getIdAuditoria(), primero.getIdAuditoria());
    }

    @Test
    void buscaUnaParteDelUsuarioSinDistinguirMayusculas() throws Exception {
        Page<AuditoriaLog> pagina = pagina(get("/auditoria").param("usuario", "  LEONEL  "));
        assertThat(pagina.getContent()).extracting(AuditoriaLog::getIdAuditoria)
                .containsExactly(segundo.getIdAuditoria(), primero.getIdAuditoria());
    }

    @Test
    void combinaTodosLosFiltrosEIncluyeElFinalDelDia() throws Exception {
        Page<AuditoriaLog> pagina = pagina(get("/auditoria").param("usuario", "leonel")
                .param("modulo", "USUARIOS").param("resultado", "FALLO")
                .param("desde", "2026-10-02").param("hasta", "2026-10-02"));
        assertThat(pagina.getContent()).extracting(AuditoriaLog::getIdAuditoria).containsExactly(segundo.getIdAuditoria());
    }

    @Test
    void laFechaFinalNoIncluyeLaMedianocheDelDiaSiguiente() throws Exception {
        Page<AuditoriaLog> pagina = pagina(get("/auditoria").param("desde", "2026-10-03").param("hasta", "2026-10-03"));
        assertThat(pagina.getContent()).extracting(AuditoriaLog::getIdAuditoria)
                .containsExactly(cuarto.getIdAuditoria(), tercero.getIdAuditoria());
    }

    @ParameterizedTest
    @ValueSource(strings = {"_", "%"})
    void losComodinesSeBuscanComoTextoLiteral(String texto) throws Exception {
        Page<AuditoriaLog> pagina = pagina(get("/auditoria").param("usuario", texto));
        assertThat(pagina.getContent()).extracting(AuditoriaLog::getIdAuditoria).containsExactly(quinto.getIdAuditoria());
    }

    @ParameterizedTest
    @ValueSource(strings = {"inexistente", "' OR 1=1--"})
    void unaBusquedaSinCoincidenciasNoEntregaTodoElHistorial(String texto) throws Exception {
        assertThat(pagina(get("/auditoria").param("usuario", texto)).getContent()).isEmpty();
    }

    @Test
    void paginaSinRepeticionesYConservaLosFiltrosEnLosEnlaces() throws Exception {
        MvcResult primera = mvc.perform(get("/auditoria").session(sesion(admin)).param("usuario", "leonel")
                        .param("modulo", "USUARIOS").param("tamano", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("pagina=1")))
                .andExpect(content().string(containsString("usuario=leonel")))
                .andExpect(content().string(containsString("modulo=USUARIOS"))).andReturn();
        assertThat(extraerPagina(primera).getContent()).extracting(AuditoriaLog::getIdAuditoria)
                .containsExactly(segundo.getIdAuditoria());
        assertThat(pagina(get("/auditoria").param("usuario", "leonel").param("modulo", "USUARIOS")
                .param("tamano", "1").param("pagina", "1")).getContent()).extracting(AuditoriaLog::getIdAuditoria)
                .containsExactly(primero.getIdAuditoria());
    }

    @ParameterizedTest
    @CsvSource({"pagina,-1", "pagina,no-numero", "tamano,0", "tamano,101", "modulo,DESCONOCIDO",
            "resultado,DESCONOCIDO", "desde,fecha-invalida", "desde,1000-01-01"})
    void parametrosInvalidosDevuelvenUnaVistaControladaSinRegistros(String nombre, String valor) throws Exception {
        MvcResult result = mvc.perform(get("/auditoria").session(sesion(admin)).param(nombre, valor))
                .andExpect(status().isBadRequest()).andExpect(view().name("auditoria/lista"))
                .andExpect(model().attributeExists("errores")).andReturn();
        assertThat(extraerPagina(result).getContent()).isEmpty();
        assertThat(logs.count()).isEqualTo(5);
    }

    @Test
    void rechazaUnIntervaloDeFechasInvertido() throws Exception {
        mvc.perform(get("/auditoria").session(sesion(admin)).param("desde", "2026-10-03").param("hasta", "2026-10-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rechazaUnFiltroDeUsuarioDemasiadoLargo() throws Exception {
        mvc.perform(get("/auditoria").session(sesion(admin)).param("usuario", "u".repeat(101)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elServicioTambienImpideConsultarSinAdministrador() {
        assertThatThrownBy(() -> consulta.consultar(noAdmin, new AuditoriaFiltroDto()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void elServicioTambienValidaElTamanoDePagina() {
        AuditoriaFiltroDto filtro = new AuditoriaFiltroDto();
        filtro.setTamano(500);
        assertThatThrownBy(() -> consulta.consultar(admin, filtro)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void escapaHtmlDelHistorialYNoPermiteCachearLaRespuesta() throws Exception {
        mvc.perform(get("/auditoria").session(sesion(admin)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("<script>alert('xss')</script>"))));
    }

    @Test
    void ocultaElMenuDeAuditoriaAlNoAdministrador() throws Exception {
        mvc.perform(get("/dashboard").session(sesion(noAdmin)))
                .andExpect(status().isOk()).andExpect(model().attribute("puedeVerAuditoria", false))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("href=\"/auditoria\""))));
        mvc.perform(get("/dashboard").session(sesion(admin)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("href=\"/auditoria\"")));
    }

    private Page<AuditoriaLog> pagina(MockHttpServletRequestBuilder request) throws Exception {
        return extraerPagina(mvc.perform(request.session(sesion(admin)))
                .andExpect(status().isOk()).andExpect(view().name("auditoria/lista")).andReturn());
    }

    @SuppressWarnings("unchecked")
    private Page<AuditoriaLog> extraerPagina(MvcResult result) {
        return (Page<AuditoriaLog>) result.getModelAndView().getModel().get("paginaAuditoria");
    }

    private MockHttpSession sesion(Usuario usuario) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogueado", usuario);
        return session;
    }

    private AuditoriaLog evento(String fecha, String usuario, ModuloAuditoria modulo, ResultadoAuditoria resultado, String detalle) {
        return logs.saveAndFlush(new AuditoriaLog(LocalDateTime.parse(fecha), null, usuario, modulo, "PRUEBA", "Usuario", 7L,
                detalle, "127.0.0.1", "POST", "/prueba", resultado));
    }

    private Rol rol(String nombre) {
        Rol rol = new Rol();
        rol.setNombre(nombre);
        rol.setArea("Administración");
        return roles.saveAndFlush(rol);
    }

    private Usuario usuario(String nombre, Rol principal) {
        Usuario usuario = new Usuario();
        usuario.setNombres("Prueba");
        usuario.setApellidos("Consulta");
        usuario.setUsuario(nombre);
        usuario.setCorreo(nombre + "@pruebas.local");
        usuario.setContrasena(encoder.encode("claveValida"));
        usuario.setRol(principal);
        usuario.setRoles(new LinkedHashSet<>(Set.of(principal)));
        usuario.setArea("Administración");
        return usuarios.saveAndFlush(usuario);
    }
}
