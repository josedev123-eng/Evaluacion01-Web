package com.evaluacion01.tecsup;

import com.evaluacion01.tecsup.audit.ZonaHoraria;
import com.evaluacion01.tecsup.dto.CitaFormulario;
import com.evaluacion01.tecsup.dto.PacienteFormulario;
import com.evaluacion01.tecsup.entity.*;
import com.evaluacion01.tecsup.repository.*;
import com.evaluacion01.tecsup.service.AutorizacionService;
import com.evaluacion01.tecsup.service.CitaService;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Evaluación 02, P5: la Recepcionista accede a Pacientes y Citas; el médico solo a sus citas.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CitasIntegrationTests {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @Autowired private MockMvc mvc;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private RolRepository roles;
    @Autowired private PermisoRepository permisos;
    @Autowired private CitaRepository citasRepo;
    @Autowired private AuditoriaLogRepository logs;
    @Autowired private PacienteService pacientes;
    @Autowired private CitaService citas;
    @Autowired private AutorizacionService autorizacion;
    @Autowired private PasswordEncoder encoder;

    private Usuario admin;
    private Usuario recepcion;
    private Usuario medico;
    private Usuario otroMedico;
    private Paciente paciente;

    @BeforeEach
    void preparar() {
        Rol rolRecepcion = rol("Recepcionista", permiso("pacientes", "VER"), permiso("pacientes", "CREAR"),
                permiso("citas", "VER"), permiso("citas", "CREAR"), permiso("citas", "EDITAR"));
        Rol rolMedico = rol("Médico", permiso("pacientes", "VER"), permiso("atenciones", "VER"),
                permiso("atenciones", "CREAR"), permiso("citas", "VER"));
        admin = usuario("adminCitas", rol("Administrador"));
        recepcion = usuario("recepcionCitas", rolRecepcion);
        medico = usuario("medicoCitas", rolMedico);
        otroMedico = usuario("otroMedicoCitas", rolMedico);
        PacienteFormulario formulario = new PacienteFormulario();
        formulario.setNombres("Paciente Ficticio");
        formulario.setApellidos("Demostración");
        formulario.setDni("87654321");
        formulario.setFechaNacimiento(LocalDate.of(1995, 5, 10));
        paciente = pacientes.guardar(admin, formulario);
    }

    @Test
    void recepcionistaProgramaUnaCitaYQuedaAuditada() throws Exception {
        mvc.perform(post("/citas/guardar").session(sesion(recepcion)).with(csrf())
                        .param("idPaciente", paciente.getIdPaciente().toString())
                        .param("idMedico", medico.getIdUsuario().toString())
                        .param("fechaHora", futuro(2)).param("motivo", "Control ficticio"))
                .andExpect(redirectedUrl("/citas"));

        Cita cita = citasRepo.findAll().get(0);
        assertThat(cita.getEstado()).isEqualTo(EstadoCita.PROGRAMADA);
        assertThat(cita.getRegistradaPor().getIdUsuario()).isEqualTo(recepcion.getIdUsuario());
        assertThat(logs.findAll()).anySatisfy(evento -> {
            assertThat(evento.getAccion()).isEqualTo("REGISTRAR_CITA");
            assertThat(evento.getUsuarioEjecutor()).isEqualTo("recepcionCitas");
            assertThat(evento.getIdEntidad()).isEqualTo(cita.getIdCita());
        });
    }

    @Test
    void recepcionistaEntraAPacientesYNoVeUsuariosNiRoles() {
        assertThat(autorizacion.tienePermiso(recepcion, "pacientes", "VER")).isTrue();
        assertThat(autorizacion.tienePermiso(recepcion, "citas", "VER")).isTrue();
        assertThat(autorizacion.tienePermiso(recepcion, "usuarios", "VER")).isFalse();
        assertThat(autorizacion.tienePermiso(recepcion, "roles", "VER")).isFalse();
        assertThat(autorizacion.rutaInicial(recepcion)).isEqualTo("/pacientes");
    }

    @Test
    void elMenuDeRecepcionIncluyePacientesYCitas() throws Exception {
        Document vista = pagina("/citas", recepcion);
        assertThat(vista.select("nav a").eachAttr("href")).containsExactly("/dashboard", "/pacientes", "/citas", "/logout");
        assertThat(vista.select("nav a[aria-current=page]").attr("href")).isEqualTo("/citas");
        assertThat(vista.select("form[action='/citas/guardar'] input[name=_csrf]")).hasSize(1);
        assertThat(vista.select("select[name=idMedico] option").eachText()).anyMatch(t -> t.contains("Profesional"));
    }

    @Test
    void medicoVeSoloSusCitasYNoPuedeProgramarNiCancelar() throws Exception {
        programar(medico, "CITA_DE_MI_CUENTA");
        programar(otroMedico, "CITA_DE_OTRO_MEDICO");

        Document vista = pagina("/citas", medico);
        assertThat(vista.text()).contains("CITA_DE_MI_CUENTA", "Mis citas").doesNotContain("CITA_DE_OTRO_MEDICO");
        assertThat(vista.select("form[action='/citas/guardar'], form[action$='/estado']")).isEmpty();

        mvc.perform(post("/citas/guardar").session(sesion(medico)).with(csrf())
                        .param("idPaciente", paciente.getIdPaciente().toString())
                        .param("idMedico", medico.getIdUsuario().toString())
                        .param("fechaHora", futuro(3)).param("motivo", "Intento no autorizado"))
                .andExpect(status().isForbidden());
        Cita propia = citasRepo.findAll().stream().filter(c -> c.getMotivo().equals("CITA_DE_MI_CUENTA")).findFirst().orElseThrow();
        mvc.perform(post("/citas/" + propia.getIdCita() + "/estado").session(sesion(medico)).with(csrf())
                        .param("estado", "CANCELADA"))
                .andExpect(status().isForbidden());
        assertThat(propia.getEstado()).isEqualTo(EstadoCita.PROGRAMADA);
        assertThat(citasRepo.count()).isEqualTo(2);
    }

    @Test
    void unaFechaPasadaSeRechazaSinGuardar() throws Exception {
        Document vista = Jsoup.parse(mvc.perform(post("/citas/guardar").session(sesion(recepcion)).with(csrf())
                        .param("idPaciente", paciente.getIdPaciente().toString())
                        .param("idMedico", medico.getIdUsuario().toString())
                        .param("fechaHora", LocalDateTime.now(ZonaHoraria.LIMA).minusDays(1).format(FORMATO))
                        .param("motivo", "Fecha pasada"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(vista.select("[role=alert]").text()).contains("futuras");
        assertThat(citasRepo.count()).isZero();
    }

    @Test
    void soloSePuedeAsignarAUnProfesionalConPermisoDeAtencion() throws Exception {
        mvc.perform(post("/citas/guardar").session(sesion(recepcion)).with(csrf())
                        .param("idPaciente", paciente.getIdPaciente().toString())
                        .param("idMedico", admin.getIdUsuario().toString())
                        .param("fechaHora", futuro(2)).param("motivo", "Asignada a quien no atiende"))
                .andExpect(status().isBadRequest());
        assertThat(citasRepo.count()).isZero();
    }

    @Test
    void elMismoMedicoNoPuedeTenerDosCitasProgramadasALaMismaHora() throws Exception {
        String hora = futuro(4);
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/citas/guardar").session(sesion(recepcion)).with(csrf())
                    .param("idPaciente", paciente.getIdPaciente().toString())
                    .param("idMedico", medico.getIdUsuario().toString())
                    .param("fechaHora", hora).param("motivo", "Misma hora " + i));
        }
        assertThat(citasRepo.count()).isEqualTo(1);
    }

    @Test
    void cancelarCambiaElEstadoSeAudita_YNoSePuedeRepetir() throws Exception {
        Cita cita = programar(medico, "Cita a cancelar");

        mvc.perform(post("/citas/" + cita.getIdCita() + "/estado").session(sesion(recepcion)).with(csrf())
                        .param("estado", "CANCELADA"))
                .andExpect(redirectedUrl("/citas")).andExpect(flash().attributeExists("exito"));
        assertThat(cita.getEstado()).isEqualTo(EstadoCita.CANCELADA);
        assertThat(logs.findAll()).anySatisfy(e -> assertThat(e.getAccion()).isEqualTo("CANCELAR_CITA"));

        mvc.perform(post("/citas/" + cita.getIdCita() + "/estado").session(sesion(recepcion)).with(csrf())
                        .param("estado", "ATENDIDA"))
                .andExpect(redirectedUrl("/citas")).andExpect(flash().attributeExists("error"));
        assertThat(cita.getEstado()).isEqualTo(EstadoCita.CANCELADA);
    }

    @Test
    void elFormularioNoAceptaEstadoNiRegistradorForjados() throws Exception {
        mvc.perform(post("/citas/guardar").session(sesion(recepcion)).with(csrf())
                        .param("idPaciente", paciente.getIdPaciente().toString())
                        .param("idMedico", medico.getIdUsuario().toString())
                        .param("fechaHora", futuro(5)).param("motivo", "Con campos forjados")
                        .param("estado", "ATENDIDA").param("idCita", "999")
                        .param("registradaPor.idUsuario", admin.getIdUsuario().toString()))
                .andExpect(redirectedUrl("/citas"));
        Cita cita = citasRepo.findAll().get(0);
        assertThat(cita.getEstado()).isEqualTo(EstadoCita.PROGRAMADA);
        assertThat(cita.getRegistradaPor().getIdUsuario()).isEqualTo(recepcion.getIdUsuario());
        assertThat(cita.getIdCita()).isNotEqualTo(999L);
    }

    @Test
    void sinSesionRedirigeAlLogin() throws Exception {
        mvc.perform(get("/citas")).andExpect(redirectedUrl("/login"));
    }

    private Cita programar(Usuario profesional, String motivo) {
        CitaFormulario formulario = new CitaFormulario();
        formulario.setIdPaciente(paciente.getIdPaciente());
        formulario.setIdMedico(profesional.getIdUsuario());
        formulario.setFechaHora(LocalDateTime.now(ZonaHoraria.LIMA).plusDays(1 + citasRepo.count()).withNano(0));
        formulario.setMotivo(motivo);
        return citas.registrar(recepcion, formulario);
    }

    private String futuro(int dias) {
        return LocalDateTime.now(ZonaHoraria.LIMA).plusDays(dias).withSecond(0).withNano(0).format(FORMATO);
    }

    private Document pagina(String ruta, Usuario usuario) throws Exception {
        return Jsoup.parse(mvc.perform(get(ruta).session(sesion(usuario))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
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
        usuario.setContrasena(encoder.encode("ClaveCitas2026"));
        return usuarios.saveAndFlush(usuario);
    }

    private MockHttpSession sesion(Usuario usuario) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogueado", usuario);
        return session;
    }
}
