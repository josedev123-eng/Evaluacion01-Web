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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
class UsuariosFrontendIntegrationTests {

    @Autowired private MockMvc mvc;
    @Autowired private RolRepository roles;
    @Autowired private PermisoRepository permisos;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private PasswordEncoder encoder;
    private Rol principal;
    private Rol adicional;
    private Rol inactivo;
    private Usuario admin;
    private Usuario editor;
    private Usuario lector;
    private Usuario destino;

    @BeforeEach
    void preparar() {
        principal = rol("Médico frontend", "VER");
        adicional = rol("Adicional frontend");
        inactivo = rol("Inactivo frontend");
        inactivo.setEstado(false);
        admin = usuario("adminUsuariosVista", rol("Administrador"));
        editor = usuario("editorUsuariosVista", rol("Editor frontend", "CREAR", "EDITAR", "VER"));
        lector = usuario("lectorUsuariosVista", principal);
        destino = usuario("destinoUsuariosVista", principal);
        destino.getRoles().add(adicional);
        usuarios.flush();
    }

    @Test
    void formularioTieneRestriccionesEtiquetasYCsrfSinMostrarClaves() throws Exception {
        Document vista = pagina("/usuarios", admin);
        assertThat(vista.selectFirst("#formUsuario input[name=_csrf]")).isNotNull();
        assertThat(vista.selectFirst("input[name=nombres]").attr("maxlength")).isEqualTo("100");
        assertThat(vista.selectFirst("input[name=dni]").attr("pattern")).isEqualTo("[0-9]{8}");
        assertThat(vista.selectFirst("input[name=contrasena]").attr("minlength")).isEqualTo("6");
        assertThat(vista.select("#formUsuario label[for]")).hasSizeGreaterThanOrEqualTo(9);
        assertThat(vista.html()).doesNotContain("hashDeFixture");
    }

    @Test
    void editarPrecargaRolesPeroNuncaLaContrasena() throws Exception {
        Document vista = pagina("/usuarios/editar/" + destino.getIdUsuario(), admin);
        assertThat(vista.selectFirst("input[name=idUsuario]").val()).isEqualTo(destino.getIdUsuario().toString());
        assertThat(vista.selectFirst("input[name=contrasena]").val()).isEmpty();
        assertThat(vista.selectFirst("input[name=contrasena]").hasAttr("required")).isFalse();
        assertThat(vista.selectFirst("input[name=idsRoles][value='" + adicional.getIdRol() + "']").hasAttr("checked")).isTrue();
    }

    @Test
    void registroMultirrolDesdeElFormularioGuardaRelacionesYListaElResultado() throws Exception {
        mvc.perform(formulario("nuevoUsuariosVista", principal).session(sesion(admin)).param("contrasena", "claveValida")
                        .param("idsRoles", adicional.getIdRol().toString()))
                .andExpect(flash().attributeExists("exito"));
        Usuario nuevo = usuarios.findByUsuario("nuevoUsuariosVista").orElseThrow();
        assertThat(nuevo.getRolesAsignados()).extracting(Rol::getIdRol).containsExactlyInAnyOrder(principal.getIdRol(), adicional.getIdRol());
        assertThat(encoder.matches("claveValida", nuevo.getContrasena())).isTrue();
        assertThat(pagina("/usuarios?q=nuevoUsuariosVista", admin).text()).contains("nuevoUsuariosVista", principal.getNombre(), adicional.getNombre());
    }

    @Test
    void editarConClaveVaciaNoReactivaNiCambiaLaClaveDeUnaCuenta() throws Exception {
        destino.setEstado(false);
        mvc.perform(formulario(destino.getUsuario(), principal).session(sesion(admin))
                        .param("idUsuario", destino.getIdUsuario().toString()).param("contrasena", "")
                        .param("idsRoles", adicional.getIdRol().toString()))
                .andExpect(flash().attributeExists("exito"));
        Usuario guardado = usuarios.findById(destino.getIdUsuario()).orElseThrow();
        assertThat(guardado.getEstado()).isFalse();
        assertThat(guardado.getContrasena()).isEqualTo("hashDeFixture");
    }

    @Test
    void editarLaPropiaCuentaBloqueaRolesYConservaCamposEnElPost() throws Exception {
        assertThat(pagina("/usuarios/editar/" + editor.getIdUsuario(), editor).select("#formUsuario .rol-adicional")).isEmpty();
        editor.getRoles().add(principal);
        usuarios.flush();
        Document vista = pagina("/usuarios/editar/" + editor.getIdUsuario(), editor);
        assertThat(vista.selectFirst("#formUsuario").attr("data-roles-bloqueados")).isEqualTo("true");
        assertThat(vista.selectFirst("select[name='rol.idRol']").hasAttr("disabled")).isTrue();
        assertThat(vista.selectFirst("select[name=area]").hasAttr("disabled")).isTrue();
        assertThat(vista.selectFirst("input[type=hidden][name='rol.idRol']").val()).isEqualTo(editor.getRol().getIdRol().toString());
        assertThat(vista.select("#formUsuario .rol-adicional input")).hasSize(1);
        assertThat(vista.selectFirst("#formUsuario .rol-adicional input").val()).isEqualTo(principal.getIdRol().toString());
        assertThat(vista.selectFirst("#formUsuario .rol-adicional input").hasAttr("disabled")).isTrue();
        assertThat(vista.select("#formUsuario .sin-roles-adicionales")).isEmpty();
        assertThat(vista.select("#formUsuario input[type=hidden][name=idsRoles]")).hasSize(2);
        mvc.perform(formulario(editor.getUsuario(), editor.getRol()).session(sesion(editor))
                        .param("idUsuario", editor.getIdUsuario().toString()).param("contrasena", "")
                        .param("idsRoles", principal.getIdRol().toString()))
                .andExpect(flash().attributeExists("exito"));
        assertThat(editor.getRolesAsignados()).hasSize(2);
    }

    @Test
    void noOfreceRolesInactivosNuevosPeroIdentificaLosYaAsignados() throws Exception {
        Document nuevo = pagina("/usuarios", admin);
        assertThat(nuevo.select("#formUsuario option[value='" + inactivo.getIdRol() + "']")).isEmpty();
        destino.getRoles().add(inactivo);
        usuarios.flush();
        Document editar = pagina("/usuarios/editar/" + destino.getIdUsuario(), admin);
        assertThat(editar.select("#formUsuario").text()).contains(inactivo.getNombre() + " (inactivo)", "no otorgan permisos");
        assertThat(editar.selectFirst("input[name=idsRoles][value='" + inactivo.getIdRol() + "']").hasAttr("checked")).isTrue();
    }

    @Test
    void unLectorSoloVeConsultaSinAltaEdicionNiEstados() throws Exception {
        Document vista = pagina("/usuarios", lector);
        assertThat(vista.selectFirst("h1").text()).isEqualTo("Consulta de usuarios");
        assertThat(vista.select("#formUsuario, form.form-estado, a[href^='/usuarios/editar/']")).isEmpty();
        assertThat(vista.selectFirst("input[name=q]")).isNotNull();
        assertThat(vista.text()).contains(destino.getUsuario());
        assertThat(pagina("/usuarios?q=" + destino.getUsuario(), lector).selectFirst("a[aria-label='Limpiar filtros']")).isNotNull();
    }

    @Test
    void errorDelBackendSeMuestraComoTextoYNoComoScript() throws Exception {
        Document vista = Jsoup.parse(mvc.perform(get("/usuarios").session(sesion(admin))
                        .flashAttr("error", "<script>alert('xss')</script>"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(vista.select(".alert-danger[role=alert]").text()).contains("<script>");
        assertThat(vista.select("script:not([src])")).isEmpty();
    }

    private Document pagina(String ruta, Usuario usuario) throws Exception {
        return Jsoup.parse(mvc.perform(get(ruta).session(sesion(usuario))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder formulario(String nombre, Rol rol) {
        return post("/usuarios/guardar").with(csrf()).param("nombres", "Prueba editada").param("apellidos", "Frontend")
                .param("usuario", nombre).param("correo", nombre + "@pruebas.local")
                .param("dni", "").param("telefono", "").param("area", "Medicina").param("rol.idRol", rol.getIdRol().toString());
    }

    private Rol rol(String nombre, String... nombresPermisos) {
        Rol rol = new Rol();
        rol.setNombre(nombre);
        rol.setArea("Medicina");
        for (String nombrePermiso : nombresPermisos) {
            Permiso permiso = permisos.findAll().stream().filter(p -> p.getModulo().equals("usuarios") && p.getNombre().equals(nombrePermiso))
                    .findFirst().orElseGet(() -> {
                        Permiso nuevo = new Permiso();
                        nuevo.setModulo("usuarios");
                        nuevo.setNombre(nombrePermiso);
                        return permisos.saveAndFlush(nuevo);
                    });
            rol.getPermisos().add(permiso);
        }
        return roles.saveAndFlush(rol);
    }

    private Usuario usuario(String nombre, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setUsuario(nombre);
        usuario.setCorreo(nombre + "@pruebas.local");
        usuario.setNombres("Prueba");
        usuario.setApellidos("Usuarios frontend");
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
