package com.evaluacion01.tecsup;

import com.evaluacion01.tecsup.entity.Rol;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.RolRepository;
import com.evaluacion01.tecsup.repository.UsuarioRepository;
import com.evaluacion01.tecsup.service.RolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Backend de roles: registro, listado, edición, estado activo/inactivo y roles base.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RolGestionIntegrationTests {

    @Autowired private MockMvc mvc;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private RolRepository roles;
    @Autowired private RolService rolService;
    @Autowired private JdbcTemplate jdbc;

    private Rol administrador;
    private Rol medico;
    private Usuario admin;

    @BeforeEach
    void prepararDatos() {
        administrador = rol("Administrador", "Administración");
        medico = rol("Médico", "Medicina");
        admin = usuario("adminPrueba", administrador);
        usuarios.flush();
    }

    // ---- Registrar, listar y editar ----

    @Test
    void registraUnRolActivoYLoMuestraEnElListado() throws Exception {
        mvc.perform(post("/roles/guardar").session(sesion(admin)).with(csrf())
                        .param("nombre", "Director").param("descripcion", "Gestión general")
                        .param("area", "Administración"))
                .andExpect(redirectedUrl("/roles"))
                .andExpect(flash().attribute("exito", "Rol guardado correctamente"));

        Rol director = roles.findByNombreIgnoreCase("Director").orElseThrow();
        assertThat(director.isActivo()).isTrue();
        assertThat(estadoEnTabla(director)).isTrue();
        mvc.perform(get("/roles").session(sesion(admin)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("roles", org.hamcrest.Matchers.hasItem(director)));
    }

    @Test
    void editaLosDatosDelRolSinCambiarSuEstado() throws Exception {
        mvc.perform(post("/roles/guardar").session(sesion(admin)).with(csrf())
                        .param("idRol", medico.getIdRol().toString())
                        .param("nombre", "Médico").param("descripcion", "Consultas y diagnósticos")
                        .param("area", "Medicina").param("estado", "false"))
                .andExpect(flash().attribute("exito", "Rol guardado correctamente"));

        assertThat(medico.getDescripcion()).isEqualTo("Consultas y diagnósticos");
        // El estado no forma parte del formulario: solo cambia por su endpoint propio.
        assertThat(medico.isActivo()).isTrue();
    }

    @Test
    void rechazaNombresDuplicadosYCamposObligatoriosVacios() throws Exception {
        mvc.perform(post("/roles/guardar").session(sesion(admin)).with(csrf())
                        .param("nombre", "médico").param("area", "Medicina"))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/roles/guardar").session(sesion(admin)).with(csrf())
                        .param("nombre", " ").param("area", "Medicina"))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/roles/guardar").session(sesion(admin)).with(csrf())
                        .param("nombre", "Sin área").param("area", ""))
                .andExpect(flash().attributeExists("error"));
        assertThat(roles.count()).isEqualTo(2);
    }

    // ---- Activar / desactivar ----

    @Test
    void administradorDesactivaYReactivaUnRol() throws Exception {
        mvc.perform(post("/roles/" + medico.getIdRol() + "/estado").session(sesion(admin)).with(csrf())
                        .param("activo", "false"))
                .andExpect(redirectedUrl("/roles"))
                .andExpect(flash().attribute("exito", "Rol Médico desactivado correctamente"));
        assertThat(medico.isActivo()).isFalse();
        assertThat(estadoEnTabla(medico)).isFalse();
        // Desactivar no borra el rol: sigue en el catálogo.
        assertThat(roles.findById(medico.getIdRol())).isPresent();

        mvc.perform(post("/roles/" + medico.getIdRol() + "/estado").session(sesion(admin)).with(csrf())
                        .param("activo", "true"))
                .andExpect(flash().attribute("exito", "Rol Médico activado correctamente"));
        assertThat(estadoEnTabla(medico)).isTrue();
    }

    @Test
    void repetirLaPeticionNoRevierteElEstado() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/roles/" + medico.getIdRol() + "/estado").session(sesion(admin)).with(csrf())
                            .param("activo", "false"))
                    .andExpect(flash().attributeExists("exito"));
        }
        assertThat(medico.isActivo()).isFalse();
    }

    @Test
    void elRolAdministradorNoSePuedeDesactivar() throws Exception {
        mvc.perform(post("/roles/" + administrador.getIdRol() + "/estado").session(sesion(admin)).with(csrf())
                        .param("activo", "false"))
                .andExpect(flash().attribute("error", "El rol Administrador es reservado y no se puede desactivar."));
        assertThat(administrador.isActivo()).isTrue();
    }

    @Test
    void soloElAdministradorCambiaElEstadoDeUnRol() throws Exception {
        Usuario clinico = usuario("clinico", medico);
        mvc.perform(post("/roles/" + medico.getIdRol() + "/estado").session(sesion(clinico)).with(csrf())
                        .param("activo", "false"))
                .andExpect(flash().attributeExists("error"));
        assertThatThrownBy(() -> rolService.cambiarEstado(medico.getIdRol(), false, clinico))
                .isInstanceOf(AccessDeniedException.class);
        // Sin sesión y sin token CSRF tampoco se ejecuta.
        mvc.perform(post("/roles/" + medico.getIdRol() + "/estado").with(csrf()).param("activo", "false"))
                .andExpect(redirectedUrl("/login"));
        mvc.perform(post("/roles/" + medico.getIdRol() + "/estado").session(sesion(admin)).param("activo", "false"))
                .andExpect(status().isForbidden());
        assertThat(medico.isActivo()).isTrue();
    }

    @Test
    void rechazaCambiarElEstadoDeUnRolInexistente() throws Exception {
        mvc.perform(post("/roles/999999/estado").session(sesion(admin)).with(csrf()).param("activo", "false"))
                .andExpect(flash().attributeExists("error"));
    }

    // ---- Roles base ----

    @Test
    void detectaLosRolesBaseQueFaltanEnElCatalogo() {
        assertThat(rolService.rolesBaseFaltantes()).containsExactly("Recepcionista");
        rol("Recepcionista", "Recepción");
        assertThat(rolService.rolesBaseFaltantes()).isEmpty();
    }

    @Test
    void elSqlDelProyectoCreaElEstadoYLosRolesBase() throws Exception {
        String schema = new ClassPathResource("schema.sql").getContentAsString(StandardCharsets.UTF_8);
        assertThat(schema).contains("estado BOOLEAN NOT NULL DEFAULT TRUE");
        assertThat(schema).contains("ALTER TABLE roles ADD COLUMN estado");
        for (String nombre : RolService.ROLES_BASE) {
            assertThat(schema).contains("('" + nombre + "',");
        }
    }

    private Boolean estadoEnTabla(Rol rol) {
        roles.flush();
        return jdbc.queryForObject("SELECT estado FROM roles WHERE id_rol = ?", Boolean.class, rol.getIdRol());
    }

    private MockHttpSession sesion(Usuario usuario) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogueado", usuario);
        return session;
    }

    private Rol rol(String nombre, String area) {
        Rol rol = new Rol();
        rol.setNombre(nombre);
        rol.setArea(area);
        return roles.save(rol);
    }

    private Usuario usuario(String nombre, Rol principal) {
        Usuario usuario = new Usuario();
        usuario.setNombres("Nombre");
        usuario.setApellidos("Prueba");
        usuario.setUsuario(nombre);
        usuario.setCorreo(nombre + "@pruebas.local");
        usuario.setContrasena("hash");
        usuario.setArea(principal.getArea());
        usuario.setRol(principal);
        usuario.setRoles(new LinkedHashSet<>(Set.of(principal)));
        return usuarios.save(usuario);
    }
}
