package com.evaluacion01.tecsup;

import com.evaluacion01.tecsup.audit.ModuloAuditoria;
import com.evaluacion01.tecsup.dto.AtencionFormulario;
import com.evaluacion01.tecsup.dto.PacienteFormulario;
import com.evaluacion01.tecsup.entity.*;
import com.evaluacion01.tecsup.repository.*;
import com.evaluacion01.tecsup.service.AtencionService;
import com.evaluacion01.tecsup.service.PacienteService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ClinicaBackendIntegrationTests {

    @Autowired private PacienteService pacientes;
    @Autowired private AtencionService atenciones;
    @Autowired private PacienteRepository pacientesRepo;
    @Autowired private AtencionRepository atencionesRepo;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private RolRepository roles;
    @Autowired private PermisoRepository permisos;
    @Autowired private AuditoriaLogRepository logs;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    private Usuario admin;
    private Usuario medico;
    private Usuario otroMedico;
    private Usuario lectorUsuarios;
    private Paciente paciente;

    @BeforeEach
    void preparar() {
        logs.deleteAll();
        Rol rolMedico = rol("Médico", true);
        admin = usuario("adminClinica", rol("Administrador", false));
        medico = usuario("medicoClinica", rolMedico);
        otroMedico = usuario("otroMedicoClinica", rolMedico);
        lectorUsuarios = usuario("lectorClinica", rol("Lector usuarios", false));
        paciente = pacientes.guardar(admin, pacienteForm("87654321"));
        logs.deleteAll();
    }

    @Test
    void pacienteNoEsUnaCuentaYSePuedeRegistrarEditarYBuscar() {
        long cuentas = usuarios.count();
        Paciente nuevo = pacientes.guardar(admin, pacienteForm("12345678"));
        assertThat(usuarios.count()).isEqualTo(cuentas);
        PacienteFormulario editado = pacienteForm(nuevo.getDni());
        editado.setIdPaciente(nuevo.getIdPaciente());
        editado.setNombres("Nombre editado");
        pacientes.guardar(admin, editado);
        assertThat(pacientes.buscar(medico, "editado")).extracting(Paciente::getIdPaciente).containsExactly(nuevo.getIdPaciente());
        assertThat(pacientes.buscar(medico, "12345678")).hasSize(1);
    }

    @Test
    void medicoConsultaPeroNoRegistraPacientes() {
        assertThat(pacientes.obtener(medico, paciente.getIdPaciente()).getDni()).isEqualTo("87654321");
        assertThatThrownBy(() -> pacientes.guardar(medico, pacienteForm("12345678"))).isInstanceOf(AccessDeniedException.class);
        assertThat(pacientesRepo.count()).isEqualTo(1);
    }

    @Test
    void medicoNoEditaDatosDemograficos() {
        PacienteFormulario form = pacienteForm(paciente.getDni());
        form.setIdPaciente(paciente.getIdPaciente());
        assertThatThrownBy(() -> pacientes.guardar(medico, form)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unaCuentaSinPermisosClinicosNoPuedeConsultar() {
        assertThatThrownBy(() -> pacientes.buscar(lectorUsuarios, null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void dniDuplicadoSeRechazaSinCrearOtraCuentaNiPaciente() {
        assertThatThrownBy(() -> pacientes.guardar(admin, pacienteForm(paciente.getDni())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("DNI");
        assertThat(pacientesRepo.count()).isEqualTo(1);
    }

    @Test
    void fechaDeNacimientoFuturaSeRechazaEnElServicio() {
        PacienteFormulario form = pacienteForm("12345678");
        form.setFechaNacimiento(LocalDate.now().plusDays(1));
        assertThatThrownBy(() -> pacientes.guardar(admin, form)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dniInvalidoSeRechazaEnElServicio() {
        assertThatThrownBy(() -> pacientes.guardar(admin, pacienteForm("abc"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void atencionUsaLaIdentidadPersistidaYLaFechaUtcDelServidor() {
        Usuario copia = new Usuario();
        copia.setIdUsuario(medico.getIdUsuario());
        copia.setUsuario("nombreForjado");
        LocalDateTime antes = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1);
        Atencion atencion = atenciones.registrar(copia, atencionForm());
        assertThat(atencion.getMedico().getUsuario()).isEqualTo(medico.getUsuario());
        assertThat(atencion.getFechaHora()).isAfterOrEqualTo(antes);
        assertThat(atencion.getPaciente().getIdPaciente()).isEqualTo(paciente.getIdPaciente());
        assertThat(logs.findAll()).singleElement().satisfies(log -> assertThat(log.getUsuarioEjecutor()).isEqualTo(medico.getUsuario()));
    }

    @Test
    void cadaMedicoSoloConsultaSusAtencionesYAdministradorConsultaTodas() {
        Atencion primera = atenciones.registrar(medico, atencionForm());
        Atencion segunda = atenciones.registrar(otroMedico, atencionForm());
        assertThat(atenciones.consultar(medico, null)).extracting(Atencion::getIdAtencion).containsExactly(primera.getIdAtencion());
        assertThat(atenciones.consultar(otroMedico, paciente.getIdPaciente())).extracting(Atencion::getIdAtencion).containsExactly(segunda.getIdAtencion());
        assertThat(atenciones.consultar(admin, paciente.getIdPaciente())).hasSize(2);
    }

    @Test
    void relacionesSePrecarganParaLaVistaYSePersistenSusClaves() {
        Atencion creada = atenciones.registrar(medico, atencionForm());
        em.flush();
        em.clear();
        List<Atencion> lista = atenciones.consultar(medico, null);
        em.clear();
        assertThat(lista.get(0).getPaciente().getNombreCompleto()).contains("Paciente");
        assertThat(lista.get(0).getMedico().getUsuario()).isEqualTo("medicoClinica");
        assertThat(jdbc.queryForObject("SELECT id_paciente FROM atenciones WHERE id_atencion = ?", Long.class, creada.getIdAtencion()))
                .isEqualTo(paciente.getIdPaciente());
        assertThat(jdbc.queryForObject("SELECT id_medico FROM atenciones WHERE id_atencion = ?", Long.class, creada.getIdAtencion()))
                .isEqualTo(medico.getIdUsuario());
    }

    @Test
    void anotacionesNoBorranHistoriaNiCuentasEnCascada() throws Exception {
        assertThat(Paciente.class.getDeclaredField("atenciones").getAnnotation(OneToMany.class).mappedBy()).isEqualTo("paciente");
        assertThat(Paciente.class.getDeclaredField("atenciones").getAnnotation(OneToMany.class).cascade()).isEmpty();
        assertThat(Atencion.class.getDeclaredField("paciente").getAnnotation(ManyToOne.class).cascade()).isEmpty();
        assertThat(Atencion.class.getDeclaredField("medico").getAnnotation(ManyToOne.class).cascade()).isEmpty();
    }

    @Test
    void auditoriaNoCopiaIdentidadDelPacienteNiTextosClinicos() {
        AtencionFormulario form = atencionForm();
        form.setDiagnostico("DIAGNOSTICO_PRIVADO_DE_PRUEBA");
        form.setIndicaciones("INDICACIONES_PRIVADAS_DE_PRUEBA");
        atenciones.registrar(medico, form);
        assertThat(logs.findAll()).singleElement().satisfies(log -> {
            assertThat(log.getModulo()).isEqualTo(ModuloAuditoria.ATENCIONES);
            assertThat(log.getDetalle()).doesNotContain(form.getMotivo(), form.getDiagnostico(), form.getIndicaciones(), paciente.getDni());
        });
    }

    @Test
    void diagnosticoDemasiadoLargoSeRechazaAntesDePersistir() {
        AtencionFormulario form = atencionForm();
        form.setDiagnostico("d".repeat(2001));
        assertThatThrownBy(() -> atenciones.registrar(medico, form)).isInstanceOf(IllegalArgumentException.class);
        assertThat(atencionesRepo.count()).isZero();
    }

    @Test
    void pacienteInexistenteNoProduceUnaAtencionHuerfana() {
        AtencionFormulario form = atencionForm();
        form.setIdPaciente(Long.MAX_VALUE);
        assertThatThrownBy(() -> atenciones.registrar(medico, form)).isInstanceOf(ResponseStatusException.class);
        assertThat(atencionesRepo.count()).isZero();
    }

    @Test
    void registrarSinPermisoEsDenegadoEnElServicio() {
        assertThatThrownBy(() -> atenciones.registrar(lectorUsuarios, atencionForm())).isInstanceOf(AccessDeniedException.class);
        assertThat(atencionesRepo.count()).isZero();
    }

    @Test
    void rolInactivoRevocaAccesoAunqueLaCuentaSigaActiva() {
        medico.getRol().setEstado(false);
        roles.flush();
        assertThatThrownBy(() -> atenciones.registrar(medico, atencionForm())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cuentaInactivaNoConservaAccesoClinico() {
        medico.setEstado(false);
        usuarios.flush();
        assertThatThrownBy(() -> pacientes.obtener(medico, paciente.getIdPaciente())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void postNoPermiteForjarProfesionalFechaOIdDeAtencion() throws Exception {
        mvc.perform(post("/atenciones/guardar").session(sesion(medico)).with(csrf())
                        .param("idPaciente", paciente.getIdPaciente().toString())
                        .param("motivo", "Consulta ficticia").param("diagnostico", "Diagnóstico ficticio")
                        .param("idAtencion", "999").param("idMedico", admin.getIdUsuario().toString())
                        .param("medico.idUsuario", admin.getIdUsuario().toString()).param("fechaHora", "2000-01-01T00:00"))
                .andExpect(redirectedUrl("/pacientes/" + paciente.getIdPaciente()));
        assertThat(atencionesRepo.findAll()).singleElement().satisfies(a -> {
            assertThat(a.getMedico().getIdUsuario()).isEqualTo(medico.getIdUsuario());
            assertThat(a.getIdAtencion()).isNotEqualTo(999L);
            assertThat(a.getFechaHora()).isAfter(LocalDateTime.of(2000, 1, 1, 0, 0));
        });
    }

    @Test
    void postSinCsrfNoRegistraAtencion() throws Exception {
        mvc.perform(post("/atenciones/guardar").session(sesion(medico))
                        .param("idPaciente", paciente.getIdPaciente().toString()).param("motivo", "Consulta")
                        .param("diagnostico", "Ficticio"))
                .andExpect(status().isForbidden());
        assertThat(atencionesRepo.count()).isZero();
        assertThat(logs.findAll()).anySatisfy(log -> assertThat(log.getModulo()).isEqualTo(ModuloAuditoria.ATENCIONES));
    }

    @Test
    void rutasClinicasSinSesionRedirigenAlLogin() throws Exception {
        for (String ruta : List.of("/pacientes", "/pacientes/" + paciente.getIdPaciente(), "/atenciones")) {
            mvc.perform(get(ruta)).andExpect(redirectedUrl("/login")).andExpect(header().string("Cache-Control", "no-store"));
        }
    }

    @Test
    void denegacionDeConsultaResponde403SinMostrarDatosClinicos() throws Exception {
        mvc.perform(get("/pacientes/" + paciente.getIdPaciente()).session(sesion(lectorUsuarios)))
                .andExpect(status().isForbidden()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(paciente.getDni()))));
    }

    private PacienteFormulario pacienteForm(String dni) {
        PacienteFormulario form = new PacienteFormulario();
        form.setNombres("Paciente");
        form.setApellidos("Ficticio");
        form.setDni(dni);
        form.setFechaNacimiento(LocalDate.of(1995, 5, 10));
        return form;
    }

    private AtencionFormulario atencionForm() {
        AtencionFormulario form = new AtencionFormulario();
        form.setIdPaciente(paciente.getIdPaciente());
        form.setMotivo("Motivo ficticio");
        form.setDiagnostico("Diagnóstico ficticio");
        return form;
    }

    private Rol rol(String nombre, boolean clinico) {
        Rol rol = new Rol();
        rol.setNombre(nombre);
        rol.setArea("Medicina");
        if (clinico) {
            rol.getPermisos().add(permiso("pacientes", "VER"));
            rol.getPermisos().add(permiso("atenciones", "VER"));
            rol.getPermisos().add(permiso("atenciones", "CREAR"));
        }
        return roles.saveAndFlush(rol);
    }

    private Permiso permiso(String modulo, String nombre) {
        Permiso permiso = new Permiso();
        permiso.setModulo(modulo);
        permiso.setNombre(nombre);
        return permisos.saveAndFlush(permiso);
    }

    private Usuario usuario(String nombre, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setUsuario(nombre);
        usuario.setCorreo(nombre + "@pruebas.local");
        usuario.setNombres("Prueba");
        usuario.setApellidos("Clínica");
        usuario.setContrasena("hashDeFixture");
        usuario.setArea("Medicina");
        usuario.setRol(rol);
        usuario.setRoles(new LinkedHashSet<>(Set.of(rol)));
        return usuarios.saveAndFlush(usuario);
    }

    private MockHttpSession sesion(Usuario usuario) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogueado", usuario);
        return session;
    }
}
