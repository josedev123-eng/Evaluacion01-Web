package com.evaluacion01.tecsup;

import com.evaluacion01.tecsup.entity.Permiso;
import com.evaluacion01.tecsup.entity.Rol;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.PermisoRepository;
import com.evaluacion01.tecsup.repository.RolRepository;
import com.evaluacion01.tecsup.repository.UsuarioRepository;
import com.evaluacion01.tecsup.service.AutorizacionService;
import com.evaluacion01.tecsup.service.RolService;
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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Seguridad del backend: accesos permitidos y rechazados según el rol, roles inactivos
// y protección del último administrador activo.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SeguridadRolesIntegrationTests {

    @Autowired private MockMvc mvc;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private RolRepository roles;
    @Autowired private PermisoRepository permisos;
    @Autowired private AutorizacionService autorizacion;
    @Autowired private RolService rolService;
    @Autowired private PasswordEncoder encoder;

    private Rol administrador;
    private Rol coordinador;
    private Rol auditor;
    private Rol medico;
    private Rol recepcionista;
    private Usuario admin;
    private Usuario operador;
    private Usuario lector;
    private Usuario clinico;
    private Usuario recepcion;

    @BeforeEach
    void prepararDatos() {
        Permiso verUsuarios = permiso("usuarios", "VER");
        Permiso crearUsuarios = permiso("usuarios", "CREAR");
        Permiso editarUsuarios = permiso("usuarios", "EDITAR");
        Permiso verRoles = permiso("roles", "VER");
        administrador = rol("Administrador", "Administración");
        coordinador = rol("Coordinador administrativo", "Administración",
                verUsuarios, crearUsuarios, editarUsuarios, verRoles);
        auditor = rol("Auditor administrativo", "Administración", verUsuarios, verRoles);
        medico = rol("Médico", "Medicina");
        recepcionista = rol("Recepcionista", "Recepción");
        admin = usuario("adminPrueba", administrador);
        operador = usuario("operador", coordinador);
        lector = usuario("lector", auditor);
        clinico = usuario("clinico", medico);
        recepcion = usuario("recepcion", recepcionista);
        usuarios.flush();
    }

    // ---- Accesos permitidos y rechazados por rol ----

    @Test
    void elAdministradorAccedeATodosLosModulos() throws Exception {
        for (String ruta : List.of("/dashboard", "/usuarios", "/roles", "/roles/nuevo", "/auditoria",
                "/roles/" + medico.getIdRol() + "/editar", "/roles/" + medico.getIdRol() + "/permisos")) {
            mvc.perform(get(ruta).session(sesion(admin))).andExpect(status().isOk());
        }
    }

    @Test
    void elCoordinadorGestionaUsuariosPeroSoloConsultaRoles() throws Exception {
        mvc.perform(get("/usuarios").session(sesion(operador))).andExpect(status().isOk())
                .andExpect(model().attribute("puedeCrear", true))
                .andExpect(model().attribute("puedeEditar", true));
        mvc.perform(get("/roles").session(sesion(operador))).andExpect(status().isOk())
                .andExpect(model().attribute("puedeEditar", false));
        mvc.perform(get("/roles/nuevo").session(sesion(operador))).andExpect(redirectedUrl("/roles"));
        mvc.perform(get("/auditoria").session(sesion(operador))).andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void elAuditorSoloConsulta() throws Exception {
        mvc.perform(get("/usuarios").session(sesion(lector))).andExpect(status().isOk())
                .andExpect(model().attribute("puedeCrear", false))
                .andExpect(model().attribute("puedeEditar", false));
        mvc.perform(get("/roles").session(sesion(lector))).andExpect(status().isOk());
        mvc.perform(post("/usuarios/" + clinico.getIdUsuario() + "/estado").session(sesion(lector)).with(csrf())
                        .param("activo", "false"))
                .andExpect(flash().attributeExists("error"));
        assertThat(clinico.getEstado()).isTrue();
    }

    @Test
    void medicoYRecepcionistaNoEntranAModulosAdministrativosPorUrlDirecta() throws Exception {
        for (Usuario usuario : List.of(clinico, recepcion)) {
            mvc.perform(get("/dashboard").session(sesion(usuario))).andExpect(status().isOk());
            mvc.perform(get("/usuarios").session(sesion(usuario))).andExpect(redirectedUrl("/dashboard"));
            mvc.perform(get("/roles").session(sesion(usuario))).andExpect(redirectedUrl("/dashboard"));
            mvc.perform(get("/auditoria").session(sesion(usuario))).andExpect(redirectedUrl("/dashboard"));
            mvc.perform(get("/roles/" + medico.getIdRol() + "/permisos").session(sesion(usuario)))
                    .andExpect(redirectedUrl("/roles"));
        }
    }

    @Test
    void lasPeticionesManipuladasDeUnRolSinPermisoNoCambianNada() throws Exception {
        mvc.perform(post("/roles/guardar").session(sesion(clinico)).with(csrf())
                        .param("nombre", "Rol forjado").param("area", "Medicina"))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/roles/" + medico.getIdRol() + "/permisos").session(sesion(clinico)).with(csrf())
                        .param("permisoIds", permisos.findAll().get(0).getIdPermiso().toString()))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/roles/" + coordinador.getIdRol() + "/estado").session(sesion(operador)).with(csrf())
                        .param("activo", "false"))
                .andExpect(flash().attributeExists("error"));
        assertThat(roles.findByNombreIgnoreCase("Rol forjado")).isEmpty();
        assertThat(medico.getPermisos()).isEmpty();
        assertThat(coordinador.isActivo()).isTrue();
    }

    @Test
    void sinSesionTodasLasRutasProtegidasRedirigenAlLogin() throws Exception {
        for (String ruta : List.of("/dashboard", "/usuarios", "/roles", "/roles/nuevo", "/auditoria")) {
            mvc.perform(get(ruta)).andExpect(redirectedUrl("/login"));
        }
        mvc.perform(post("/roles/" + medico.getIdRol() + "/estado").with(csrf()).param("activo", "false"))
                .andExpect(redirectedUrl("/login"));
    }

    // ---- Roles inactivos ----

    @Test
    void desactivarUnRolRetiraSusPermisosDeInmediatoAunConLaSesionAbierta() throws Exception {
        MockHttpSession sesionAbierta = sesion(operador);
        mvc.perform(get("/usuarios").session(sesionAbierta)).andExpect(status().isOk());

        rolService.cambiarEstado(coordinador.getIdRol(), false, admin);

        mvc.perform(get("/usuarios").session(sesionAbierta)).andExpect(redirectedUrl("/dashboard"));
        mvc.perform(get("/roles").session(sesionAbierta)).andExpect(redirectedUrl("/dashboard"));
        mvc.perform(post("/usuarios/" + clinico.getIdUsuario() + "/estado").session(sesionAbierta).with(csrf())
                        .param("activo", "false"))
                .andExpect(flash().attributeExists("error"));
        assertThat(clinico.getEstado()).isTrue();
        assertThat(autorizacion.tienePermiso(operador, "usuarios", "VER")).isFalse();
    }

    @Test
    void unRolAdicionalActivoSigueOtorgandoSusPermisos() throws Exception {
        operador.getRoles().add(auditor);
        usuarios.saveAndFlush(operador);
        rolService.cambiarEstado(coordinador.getIdRol(), false, admin);

        mvc.perform(get("/usuarios").session(sesion(operador))).andExpect(status().isOk())
                .andExpect(model().attribute("puedeEditar", false));
    }

    @Test
    void alReactivarElRolSeRecuperanLosPermisos() throws Exception {
        rolService.cambiarEstado(coordinador.getIdRol(), false, admin);
        rolService.cambiarEstado(coordinador.getIdRol(), true, admin);

        mvc.perform(get("/usuarios").session(sesion(operador))).andExpect(status().isOk())
                .andExpect(model().attribute("puedeEditar", true));
    }

    // ---- Último administrador activo ----

    @Test
    void seMantieneLaProteccionDelUltimoAdministradorActivo() throws Exception {
        mvc.perform(post("/usuarios/" + admin.getIdUsuario() + "/estado").session(sesion(admin)).with(csrf())
                        .param("activo", "false"))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/roles/" + administrador.getIdRol() + "/estado").session(sesion(admin)).with(csrf())
                        .param("activo", "false"))
                .andExpect(flash().attributeExists("error"));
        assertThat(admin.getEstado()).isTrue();
        assertThat(administrador.isActivo()).isTrue();
        assertThat(usuarios.contarActivosConRol("Administrador")).isEqualTo(1);
        assertThat(autorizacion.esAdministrador(admin)).isTrue();
    }

    private MockHttpSession sesion(Usuario usuario) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogueado", usuario);
        return session;
    }

    private Permiso permiso(String modulo, String nombre) {
        Permiso permiso = new Permiso();
        permiso.setModulo(modulo);
        permiso.setNombre(nombre);
        return permisos.save(permiso);
    }

    private Rol rol(String nombre, String area, Permiso... asignados) {
        Rol rol = new Rol();
        rol.setNombre(nombre);
        rol.setArea(area);
        rol.setPermisos(new LinkedHashSet<>(List.of(asignados)));
        return roles.save(rol);
    }

    private Usuario usuario(String nombre, Rol principal) {
        Usuario usuario = new Usuario();
        usuario.setNombres("Nombre");
        usuario.setApellidos("Prueba");
        usuario.setUsuario(nombre);
        usuario.setCorreo(nombre + "@pruebas.local");
        usuario.setContrasena(encoder.encode("claveValida"));
        usuario.setArea(principal.getArea());
        usuario.setRol(principal);
        usuario.setRoles(new LinkedHashSet<>(Set.of(principal)));
        return usuarios.save(usuario);
    }
}
