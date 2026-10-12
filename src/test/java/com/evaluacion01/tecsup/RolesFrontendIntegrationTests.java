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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
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
class RolesFrontendIntegrationTests {

    @Autowired private MockMvc mvc;
    @Autowired private RolRepository roles;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private PermisoRepository permisos;
    private Usuario admin;
    private Usuario lector;
    private Rol administrador;
    private Rol activo;
    private Rol inactivo;

    @BeforeEach
    void preparar() {
        administrador = rol("Administrador", true);
        activo = rol("Médico", true);
        inactivo = rol("Recepcionista", false);
        Rol consulta = rol("Consulta roles", true);
        Permiso permiso = new Permiso();
        permiso.setModulo("roles");
        permiso.setNombre("VER");
        consulta.getPermisos().add(permisos.saveAndFlush(permiso));
        roles.flush();
        admin = usuario("adminFrontendRoles", administrador);
        lector = usuario("lectorFrontendRoles", consulta);
    }

    @Test
    void muestraEstadoAccionesExplicitasYCsrfSinDesactivarAdministrador() throws Exception {
        Document vista = pagina("/roles", admin);
        String selector = "[data-rol-id='" + activo.getIdRol() + "']";
        assertThat(vista.select(selector).text()).contains("Activo", "Desactivar");
        assertThat(vista.selectFirst(selector + " form").attr("method")).isEqualTo("post");
        assertThat(vista.selectFirst(selector + " input[name=activo]").val()).isEqualTo("false");
        assertThat(vista.select(selector + " input[name=_csrf]")).hasSize(1);
        assertThat(vista.select("[data-rol-id='" + inactivo.getIdRol() + "']").text()).contains("Inactivo", "Activar");
        assertThat(vista.select("[data-rol-id='" + administrador.getIdRol() + "'] form")).isEmpty();
        assertThat(vista.text()).contains("Rol reservado");
    }

    @Test
    void muestraElResultadoDeDesactivarYReactivarDesdeElListado() throws Exception {
        mvc.perform(post("/roles/" + activo.getIdRol() + "/estado").session(sesion(admin)).with(csrf()).param("activo", "false"))
                .andExpect(redirectedUrl("/roles")).andExpect(flash().attributeExists("exito"));
        assertThat(pagina("/roles", admin).select("[data-rol-id='" + activo.getIdRol() + "']").text())
                .contains("Inactivo", "Activar").doesNotContain("Desactivar");
        mvc.perform(post("/roles/" + activo.getIdRol() + "/estado").session(sesion(admin)).with(csrf()).param("activo", "true"))
                .andExpect(flash().attributeExists("exito"));
        assertThat(pagina("/roles", admin).select("[data-rol-id='" + activo.getIdRol() + "']").text()).contains("Activo", "Desactivar");
    }

    @Test
    void unLectorNoVeAccionesYUnPostForjadoSeRechaza() throws Exception {
        Document vista = pagina("/roles", lector);
        assertThat(vista.select("form.form-estado, a[href$='/editar'], a[href$='/permisos'], a[href='/roles/nuevo']")).isEmpty();
        mvc.perform(post("/roles/" + activo.getIdRol() + "/estado").session(sesion(lector)).with(csrf()).param("activo", "false"))
                .andExpect(flash().attributeExists("error"));
        assertThat(roles.findById(activo.getIdRol()).orElseThrow().isActivo()).isTrue();
    }

    @Test
    void laPeticionDeEstadoSinCsrfNoModificaElRol() throws Exception {
        mvc.perform(post("/roles/" + activo.getIdRol() + "/estado").session(sesion(admin)).param("activo", "false"))
                .andExpect(status().isForbidden());
        assertThat(roles.findById(activo.getIdRol()).orElseThrow().isActivo()).isTrue();
    }

    @Test
    void muestraElRechazoDelBackendSinInterpretarloComoHtml() throws Exception {
        Document vista = Jsoup.parse(mvc.perform(get("/roles").session(sesion(admin))
                        .flashAttr("error", "<script>alert('xss')</script>"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(vista.select(".alert-danger[role=alert]").text()).contains("<script>");
        assertThat(vista.select("script:not([src])")).isEmpty();
    }

    @Test
    void editarUnRolInactivoInformaQueElEstadoSeCambiaEnElListado() throws Exception {
        assertThat(pagina("/roles/" + inactivo.getIdRol() + "/editar", admin).text()).contains("Editar sus datos no lo reactiva");
        assertThat(pagina("/roles/" + administrador.getIdRol() + "/editar", admin).selectFirst("input[name=nombre]").hasAttr("readonly")).isTrue();
    }

    private Document pagina(String ruta, Usuario usuario) throws Exception {
        return Jsoup.parse(mvc.perform(get(ruta).session(sesion(usuario))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private Rol rol(String nombre, boolean activo) {
        Rol rol = new Rol();
        rol.setNombre(nombre);
        rol.setArea("Medicina");
        rol.setEstado(activo);
        return roles.saveAndFlush(rol);
    }

    private Usuario usuario(String nombre, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setUsuario(nombre);
        usuario.setCorreo(nombre + "@pruebas.local");
        usuario.setNombres("Prueba");
        usuario.setApellidos("Frontend roles");
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
