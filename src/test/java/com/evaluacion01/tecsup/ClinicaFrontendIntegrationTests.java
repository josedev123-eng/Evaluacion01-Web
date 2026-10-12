package com.evaluacion01.tecsup;

import com.evaluacion01.tecsup.dto.AtencionFormulario;
import com.evaluacion01.tecsup.dto.PacienteFormulario;
import com.evaluacion01.tecsup.entity.*;
import com.evaluacion01.tecsup.repository.*;
import com.evaluacion01.tecsup.service.AtencionService;
import com.evaluacion01.tecsup.service.AutorizacionService;
import com.evaluacion01.tecsup.service.PacienteService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ClinicaFrontendIntegrationTests {

    @Autowired private MockMvc mvc;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private RolRepository roles;
    @Autowired private PermisoRepository permisos;
    @Autowired private PacienteRepository pacientesRepo;
    @Autowired private AtencionRepository atencionesRepo;
    @Autowired private PacienteService pacientes;
    @Autowired private AtencionService atenciones;
    @Autowired private AutorizacionService autorizacion;
    @Autowired private PasswordEncoder encoder;
    private Usuario admin;
    private Usuario medico;
    private Usuario otroMedico;
    private Usuario lectorUsuarios;
    private Rol rolMedico;
    private Paciente paciente;

    @BeforeEach
    void preparar() {
        rolMedico = rol("Médico", permiso("pacientes", "VER"), permiso("atenciones", "VER"), permiso("atenciones", "CREAR"));
        admin = usuario("adminVistaClinica", rol("Administrador"));
        medico = usuario("medicoVistaClinica", rolMedico);
        otroMedico = usuario("otroMedicoVistaClinica", rolMedico);
        lectorUsuarios = usuario("lectorVistaClinica", rol("Consulta usuarios", permiso("usuarios", "VER")));
        PacienteFormulario formulario = new PacienteFormulario();
        formulario.setNombres("Paciente Ficticio");
        formulario.setApellidos("Demostración");
        formulario.setDni("87654321");
        formulario.setFechaNacimiento(LocalDate.of(1995, 5, 10));
        paciente = pacientes.guardar(admin, formulario);
    }

    @Test
    void medicoVePacientesSinControlesDeGestionNiCuentasDePersonal() throws Exception {
        Document vista = pagina("/pacientes", medico);
        assertThat(vista.select("nav a").eachAttr("href")).containsExactly("/dashboard", "/pacientes", "/atenciones", "/logout");
        assertThat(vista.select("nav a[aria-current=page]").attr("href")).isEqualTo("/pacientes");
        assertThat(vista.select("a[href='/pacientes/nuevo'], a[href$='/editar']")).isEmpty();
        assertThat(vista.select("tbody").text()).contains(paciente.getDni(), paciente.getNombreCompleto());
    }

    @Test
    void administradorPuedeAbrirRegistroYEdicionDePacientes() throws Exception {
        Document vista = pagina("/pacientes", admin);
        assertThat(vista.select("a[href='/pacientes/nuevo']")).hasSize(1);
        assertThat(vista.select("a[href='/pacientes/" + paciente.getIdPaciente() + "/editar']")).hasSize(1);
        assertThat(vista.select("nav a").eachAttr("href")).contains("/usuarios", "/roles", "/auditoria");
    }

    @Test
    void fichaMedicaMuestraSoloNotasPropiasYFormularioFirmadoEnServidor() throws Exception {
        registrar(medico, "NOTA_PROPIA_FICTICIA");
        registrar(otroMedico, "NOTA_AJENA_FICTICIA");
        Document vista = pagina(rutaPaciente(), medico);
        assertThat(vista.text()).contains("NOTA_PROPIA_FICTICIA", "Mis atenciones").doesNotContain("NOTA_AJENA_FICTICIA");
        assertThat(vista.select("form[action='/atenciones/guardar'] input[name=_csrf]")).hasSize(1);
        assertThat(vista.select("input[name=idMedico], input[name=fechaHora], input[name=idAtencion], a[href$='/editar']")).isEmpty();
        assertThat(vista.select("textarea[name=diagnostico]").attr("maxlength")).isEqualTo("2000");
    }

    @Test
    void administradorRevisaTodasLasAtencionesDelPaciente() throws Exception {
        registrar(medico, "PRIMERA_NOTA_FICTICIA");
        registrar(otroMedico, "SEGUNDA_NOTA_FICTICIA");
        Document vista = pagina(rutaPaciente(), admin);
        assertThat(vista.select("article.registro-atencion")).hasSize(2);
        assertThat(vista.text()).contains("PRIMERA_NOTA_FICTICIA", "SEGUNDA_NOTA_FICTICIA", "Atenciones del paciente");
    }

    @Test
    void listadoDeAtencionesIgnoraFiltrosDeProfesionalForjados() throws Exception {
        registrar(medico, "NOTA_DE_MI_CUENTA");
        registrar(otroMedico, "NOTA_DE_OTRA_CUENTA");
        Document vista = pagina("/atenciones?idMedico=" + otroMedico.getIdUsuario(), medico);
        assertThat(vista.text()).contains("NOTA_DE_MI_CUENTA").doesNotContain("NOTA_DE_OTRA_CUENTA");
        assertThat(vista.select("article a").attr("href")).isEqualTo(rutaPaciente());
        assertThat(vista.select("nav a[aria-current=page]").attr("href")).isEqualTo("/atenciones");
        assertThat(pagina("/atenciones", admin).select("article.registro-atencion")).hasSize(2);
    }

    @Test
    void formularioDePacienteTieneCsrfSinCamposDeCuentaYLimitaFechaFutura() throws Exception {
        Document vista = pagina("/pacientes/nuevo", admin);
        assertThat(vista.select("input[name=_csrf]")).hasSize(1);
        assertThat(vista.select("input[name=contrasena], input[name=usuario], input[name=idMedico]")).isEmpty();
        assertThat(vista.select("input[name=dni]").attr("maxlength")).isEqualTo("8");
        assertThat(vista.select("input[name=fechaNacimiento]").attr("max")).isEqualTo(LocalDate.now().toString());
    }

    @Test
    void formularioDeEdicionPrecargaFechaIsoYDatosPersistidos() throws Exception {
        Document vista = pagina(rutaPaciente() + "/editar", admin);
        assertThat(vista.select("input[name=fechaNacimiento]").val()).isEqualTo("1995-05-10");
        assertThat(vista.select("input[name=dni]").val()).isEqualTo(paciente.getDni());
        assertThat(vista.select("input[name=idPaciente]").val()).isEqualTo(paciente.getIdPaciente().toString());
    }

    @Test
    void registrarPacienteDesdeLaVistaNoCreaUnaCuenta() throws Exception {
        long cuentas = usuarios.count();
        mvc.perform(post("/pacientes/guardar").session(sesion(admin)).with(csrf())
                        .param("nombres", "Otro paciente").param("apellidos", "Ficticio").param("dni", "12345678")
                        .param("fechaNacimiento", "2000-01-01").param("telefono", ""))
                .andExpect(status().is3xxRedirection());
        assertThat(pacientesRepo.count()).isEqualTo(2);
        assertThat(usuarios.count()).isEqualTo(cuentas);
        assertThat(pagina("/pacientes?q=12345678", medico).select("tbody tr")).hasSize(1);
    }

    @Test
    void dniDuplicadoMuestraErrorSinPerderElFormulario() throws Exception {
        Document vista = Jsoup.parse(mvc.perform(post("/pacientes/guardar").session(sesion(admin)).with(csrf())
                        .param("nombres", "Paciente duplicado").param("apellidos", "Ficticio").param("dni", paciente.getDni())
                        .param("fechaNacimiento", "2000-01-01"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(vista.select("[role=alert]").text()).contains("Ya existe un paciente con ese DNI");
        assertThat(vista.select("input[name=nombres]").val()).isEqualTo("Paciente duplicado");
        assertThat(pacientesRepo.count()).isEqualTo(1);
    }

    @Test
    void fechaFuturaMuestraValidacionDeServidor() throws Exception {
        Document vista = Jsoup.parse(mvc.perform(post("/pacientes/guardar").session(sesion(admin)).with(csrf())
                        .param("nombres", "Paciente").param("apellidos", "Ficticio").param("dni", "12345678")
                        .param("fechaNacimiento", LocalDate.now().plusDays(1).toString()))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(vista.select("[role=alert]").text()).contains("no puede ser futura");
        assertThat(pacientesRepo.count()).isEqualTo(1);
    }

    @Test
    void diagnosticoVacioDevuelveLaFichaConErrorYSinGuardar() throws Exception {
        Document vista = Jsoup.parse(mvc.perform(post("/atenciones/guardar").session(sesion(medico)).with(csrf())
                        .param("idPaciente", paciente.getIdPaciente().toString()).param("motivo", "Motivo que se conserva")
                        .param("diagnostico", ""))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(vista.select("[role=alert]").text()).contains("El diagnóstico es obligatorio");
        assertThat(vista.select("textarea[name=motivo]").val()).isEqualTo("Motivo que se conserva");
        assertThat(atencionesRepo.count()).isZero();
    }

    @Test
    void diagnosticoDemasiadoLargoNoSePersisteDesdeElFormulario() throws Exception {
        mvc.perform(post("/atenciones/guardar").session(sesion(medico)).with(csrf())
                        .param("idPaciente", paciente.getIdPaciente().toString()).param("motivo", "Consulta ficticia")
                        .param("diagnostico", "d".repeat(2001)))
                .andExpect(status().isBadRequest());
        assertThat(atencionesRepo.count()).isZero();
    }

    @Test
    void textosClinicosSeEscapanEnLaVista() throws Exception {
        String texto = "<script>alert('prueba')</script>";
        registrar(medico, texto);
        Document vista = pagina(rutaPaciente(), medico);
        assertThat(vista.select("article script")).isEmpty();
        assertThat(vista.select("article").text()).contains(texto);
    }

    @Test
    void medicoNoPuedeForzarRegistroEdicionOActualizacionDePaciente() throws Exception {
        mvc.perform(get("/pacientes/nuevo").session(sesion(medico))).andExpect(status().isForbidden());
        mvc.perform(get(rutaPaciente() + "/editar").session(sesion(medico))).andExpect(status().isForbidden());
        mvc.perform(post("/pacientes/guardar").session(sesion(medico)).with(csrf())
                        .param("idPaciente", paciente.getIdPaciente().toString()).param("nombres", "Cambio forzado"))
                .andExpect(status().isForbidden());
        assertThat(pacientesRepo.findById(paciente.getIdPaciente()).orElseThrow().getNombres()).isEqualTo("Paciente Ficticio");
    }

    @Test
    void lectoresAdministrativosConservanSuAccesoPeroNoElClinico() throws Exception {
        assertThat(pagina("/usuarios", lectorUsuarios).select("nav a").eachAttr("href")).containsExactly("/dashboard", "/usuarios", "/logout");
        mvc.perform(get("/pacientes").session(sesion(lectorUsuarios))).andExpect(status().isForbidden());
        mvc.perform(get("/atenciones").session(sesion(lectorUsuarios))).andExpect(status().isForbidden());
        assertThat(autorizacion.rutaInicial(lectorUsuarios)).isEqualTo("/usuarios");
    }

    @Test
    void identificadoresInvalidosMuestran400O404SinSqlNiTrazas() throws Exception {
        mvc.perform(get("/pacientes/abc").session(sesion(medico))).andExpect(status().isBadRequest());
        String cuerpo = mvc.perform(get("/pacientes/" + Long.MAX_VALUE).session(sesion(medico)))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(cuerpo).doesNotContain("SELECT", "org.hibernate", "Stacktrace");
    }

    @Test
    void unRolClinicoAdicionalHabilitaElFlujoYElLoginSinConcederAdministracion() throws Exception {
        Usuario multirrol = usuario("multirrolClinico", rol("Apoyo sin permisos"));
        multirrol.getRoles().add(rolMedico);
        usuarios.flush();
        Document vista = pagina("/dashboard", multirrol);
        assertThat(vista.select(".dashboard-action").eachAttr("href")).containsExactly("/pacientes", "/atenciones");
        mvc.perform(post("/login").with(csrf()).param("identificador", multirrol.getUsuario()).param("contrasena", "ClaveVista2026"))
                .andExpect(redirectedUrl("/pacientes"));
        mvc.perform(post("/atenciones/guardar").session(sesion(multirrol)).with(csrf())
                        .param("idPaciente", paciente.getIdPaciente().toString()).param("motivo", "Consulta multirrol ficticia")
                        .param("diagnostico", "Diagnóstico ficticio"))
                .andExpect(redirectedUrl(rutaPaciente()));
    }

    @Test
    void revocarPermisosClinicosActualizaLaSesionExistente() throws Exception {
        MockHttpSession session = sesion(medico);
        mvc.perform(get(rutaPaciente()).session(session)).andExpect(status().isOk());
        rolMedico.getPermisos().removeIf(p -> "atenciones".equals(p.getModulo()));
        roles.flush();
        mvc.perform(get("/atenciones").session(session)).andExpect(status().isForbidden());
        Document vista = Jsoup.parse(mvc.perform(get(rutaPaciente()).session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(vista.select("form[action='/atenciones/guardar'], article.registro-atencion, nav a[href='/atenciones']")).isEmpty();
    }

    @Test
    void desactivarLaCuentaCierraSuSesionClinica() throws Exception {
        MockHttpSession session = sesion(medico);
        medico.setEstado(false);
        usuarios.flush();
        mvc.perform(get("/pacientes").session(session)).andExpect(redirectedUrl("/login?cuentaInactiva"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void verAtencionesSinVerPacientesNoHabilitaMenuNiConsulta() throws Exception {
        Usuario aislado = usuario("permisoIncompleto", rol("Atenciones sin pacientes", permiso("atenciones", "VER")));
        assertThat(pagina("/dashboard", aislado).select(".dashboard-action")).isEmpty();
        mvc.perform(get("/atenciones").session(sesion(aislado))).andExpect(status().isForbidden());
    }

    private String rutaPaciente() {
        return "/pacientes/" + paciente.getIdPaciente();
    }

    private Document pagina(String ruta, Usuario usuario) throws Exception {
        return Jsoup.parse(mvc.perform(get(ruta).session(sesion(usuario))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private void registrar(Usuario usuario, String diagnostico) {
        AtencionFormulario formulario = new AtencionFormulario();
        formulario.setIdPaciente(paciente.getIdPaciente());
        formulario.setMotivo("Consulta ficticia");
        formulario.setDiagnostico(diagnostico);
        atenciones.registrar(usuario, formulario);
    }

    private Permiso permiso(String modulo, String nombre) {
        return permisos.findAll().stream().filter(p -> modulo.equals(p.getModulo()) && nombre.equals(p.getNombre())).findFirst()
                .orElseGet(() -> {
                    Permiso permiso = new Permiso();
                    permiso.setModulo(modulo);
                    permiso.setNombre(nombre);
                    return permisos.saveAndFlush(permiso);
                });
    }

    private Rol rol(String nombre, Permiso... asignados) {
        Rol rol = new Rol();
        rol.setNombre(nombre);
        rol.setArea("Medicina");
        rol.setPermisos(new LinkedHashSet<>(Set.of(asignados)));
        return roles.saveAndFlush(rol);
    }

    private Usuario usuario(String nombre, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setUsuario(nombre);
        usuario.setCorreo(nombre + "@pruebas.local");
        usuario.setNombres("Profesional");
        usuario.setApellidos("Ficticio");
        usuario.setArea("Medicina");
        usuario.setRol(rol);
        usuario.setRoles(new LinkedHashSet<>(Set.of(rol)));
        usuario.setContrasena(encoder.encode("ClaveVista2026"));
        return usuarios.saveAndFlush(usuario);
    }

    private MockHttpSession sesion(Usuario usuario) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogueado", usuario);
        return session;
    }
}
