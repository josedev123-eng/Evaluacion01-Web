package com.evaluacion01.tecsup;

import com.evaluacion01.tecsup.entity.Permiso;
import com.evaluacion01.tecsup.entity.Rol;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.PermisoRepository;
import com.evaluacion01.tecsup.repository.RolRepository;
import com.evaluacion01.tecsup.repository.UsuarioRepository;
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

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NavegacionFrontendIntegrationTests {

    @Autowired private MockMvc mvc;
    @Autowired private RolRepository roles;
    @Autowired private PermisoRepository permisos;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private PasswordEncoder encoder;
    private Usuario admin;
    private Usuario medico;
    private Usuario consultaRoles;
    private Rol rolMedico;

    @BeforeEach
    void preparar() {
        rolMedico = rol("Médico", "usuarios");
        admin = usuario("adminNav", rol("Administrador", null));
        medico = usuario("medicoNav", rolMedico);
        consultaRoles = usuario("rolesNav", rol("Consulta roles", "roles"));
    }

    @Test
    void administradorVeLosModulosYLaSeccionActualEnTodasLasVistas() throws Exception {
        Map<String, String> rutas = Map.of("/dashboard", "/dashboard", "/usuarios", "/usuarios", "/roles", "/roles",
                "/roles/nuevo", "/roles", "/roles/" + rolMedico.getIdRol() + "/permisos", "/roles", "/auditoria", "/auditoria");
        for (var ruta : rutas.entrySet()) {
            Document vista = pagina(ruta.getKey(), sesion(admin));
            assertThat(vista.select("nav[aria-label='Navegación principal'] a").eachAttr("href"))
                    .containsExactly("/dashboard", "/usuarios", "/roles", "/auditoria", "/logout");
            assertThat(vista.select("nav a[aria-current=page]").eachAttr("href")).containsExactly(ruta.getValue());
        }
    }

    @Test
    void medicoSoloVeUsuariosYUnaUrlDeAuditoriaSigueDenegada() throws Exception {
        Document vista = pagina("/dashboard", sesion(medico));
        assertThat(vista.select("nav a").eachAttr("href")).containsExactly("/dashboard", "/usuarios", "/logout");
        assertThat(vista.select(".dashboard-action").eachAttr("href")).containsExactly("/usuarios");
        mvc.perform(get("/auditoria").session(sesion(medico))).andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void consultaRolesSoloVeEseModuloSinGestionAdministrativa() throws Exception {
        Document vista = pagina("/roles", sesion(consultaRoles));
        assertThat(vista.select("nav a").eachAttr("href")).containsExactly("/dashboard", "/roles", "/logout");
        assertThat(vista.select("form.form-estado, a[href='/roles/nuevo']")).isEmpty();
    }

    @Test
    void desactivarElRolActualizaUnaSesionExistenteYElMenuDelDashboard() throws Exception {
        MockHttpSession anterior = sesion(medico);
        rolMedico.setEstado(false);
        roles.flush();
        mvc.perform(get("/usuarios").session(anterior)).andExpect(redirectedUrl("/dashboard"));
        Document vista = pagina("/dashboard", anterior);
        assertThat(vista.select("nav a").eachAttr("href")).containsExactly("/dashboard", "/logout");
        assertThat(vista.select(".dashboard-action")).isEmpty();
    }

    @Test
    void loginConservaLaRedireccionSegunElModuloAutorizado() throws Exception {
        mvc.perform(post("/login").with(csrf()).param("identificador", medico.getUsuario()).param("contrasena", "claveNav"))
                .andExpect(redirectedUrl("/usuarios"));
        mvc.perform(post("/login").with(csrf()).param("identificador", consultaRoles.getUsuario()).param("contrasena", "claveNav"))
                .andExpect(redirectedUrl("/roles"));
        mvc.perform(post("/login").with(csrf()).param("identificador", admin.getUsuario()).param("contrasena", "claveNav"))
                .andExpect(redirectedUrl("/dashboard"));
    }

    private Document pagina(String ruta, MockHttpSession session) throws Exception {
        return Jsoup.parse(mvc.perform(get(ruta).session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private Rol rol(String nombre, String modulo) {
        Rol rol = new Rol();
        rol.setNombre(nombre);
        rol.setArea("Medicina");
        if (modulo != null) {
            Permiso permiso = new Permiso();
            permiso.setNombre("VER");
            permiso.setModulo(modulo);
            rol.getPermisos().add(permisos.saveAndFlush(permiso));
        }
        return roles.saveAndFlush(rol);
    }

    private Usuario usuario(String nombre, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setUsuario(nombre);
        usuario.setCorreo(nombre + "@pruebas.local");
        usuario.setNombres("Prueba");
        usuario.setApellidos("Navegación");
        usuario.setContrasena(encoder.encode("claveNav"));
        usuario.setRol(rol);
        usuario.setRoles(new LinkedHashSet<>(Set.of(rol)));
        usuario.setArea("Medicina");
        return usuarios.saveAndFlush(usuario);
    }

    private MockHttpSession sesion(Usuario usuario) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogueado", usuario);
        return session;
    }
}
