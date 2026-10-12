package com.evaluacion01.tecsup;

import com.evaluacion01.tecsup.entity.Permiso;
import com.evaluacion01.tecsup.entity.Rol;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.PermisoRepository;
import com.evaluacion01.tecsup.repository.RolRepository;
import com.evaluacion01.tecsup.repository.UsuarioRepository;
import com.evaluacion01.tecsup.service.RolService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.ManyToMany;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
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
class PermisoRolInversoIntegrationTests {

    @Autowired private MockMvc mvc;
    @Autowired private PermisoRepository permisos;
    @Autowired private RolRepository roles;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private RolService service;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    private Permiso ver;
    private Permiso editar;
    private Rol primero;
    private Rol segundo;
    private Usuario admin;
    private Usuario lector;

    @BeforeEach
    void preparar() {
        ver = permiso("VER");
        editar = permiso("EDITAR");
        primero = rol("Médico inverso", ver);
        segundo = rol("Recepcionista inverso", ver);
        admin = usuario("adminInverso", rol("Administrador"));
        lector = usuario("lectorInverso", primero);
        em.flush();
        em.clear();
    }

    @Test
    void utilizaMappedBySinCascadasNiTablaDuplicada() throws Exception {
        ManyToMany relacion = Permiso.class.getDeclaredField("roles").getAnnotation(ManyToMany.class);
        assertThat(relacion.mappedBy()).isEqualTo("permisos");
        assertThat(relacion.cascade()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'PERMISO_ROLES'", Long.class)).isZero();
    }

    @Test
    void consultaLosRolesDeUnPermisoDesdeElLadoInverso() {
        Permiso permiso = permisos.findById(ver.getIdPermiso()).orElseThrow();
        assertThat(permiso.getRoles()).extracting(Rol::getIdRol).containsExactlyInAnyOrder(primero.getIdRol(), segundo.getIdRol());
        assertThat(permisos.findById(editar.getIdPermiso()).orElseThrow().getRoles()).isEmpty();
    }

    @Test
    void cambiarSoloElLadoInversoNoEscribeLaAsociacion() {
        permisos.findById(ver.getIdPermiso()).orElseThrow().getRoles().clear();
        em.flush();
        em.clear();
        assertThat(permisos.findById(ver.getIdPermiso()).orElseThrow().getRoles()).hasSize(2);
    }

    @Test
    void asignarYRevocarDesdeLaInterfazCoincideConLaTablaYNoBorraElCatalogo() throws Exception {
        mvc.perform(post("/roles/" + primero.getIdRol() + "/permisos").session(sesion(admin)).with(csrf())
                        .param("permisoIds", editar.getIdPermiso().toString()))
                .andExpect(redirectedUrl("/roles")).andExpect(flash().attributeExists("exito"));
        em.flush();
        em.clear();
        assertThat(permisos.findById(ver.getIdPermiso()).orElseThrow().getRoles()).extracting(Rol::getIdRol)
                .containsExactly(segundo.getIdRol());
        assertThat(permisos.findById(editar.getIdPermiso()).orElseThrow().getRoles()).extracting(Rol::getIdRol)
                .containsExactly(primero.getIdRol());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rol_permisos WHERE id_rol = ? AND id_permiso = ?", Long.class,
                primero.getIdRol(), editar.getIdPermiso())).isEqualTo(1);
        MvcResult resultado = mvc.perform(get("/roles/" + primero.getIdRol() + "/permisos").session(sesion(admin)))
                .andExpect(status().isOk()).andReturn();
        Document vista = Jsoup.parse(resultado.getResponse().getContentAsString());
        assertThat(vista.selectFirst("input[name=permisoIds][value='" + editar.getIdPermiso() + "']").hasAttr("checked")).isTrue();
        assertThat(vista.select("[data-permiso-id='" + editar.getIdPermiso() + "'] .roles-asociados").text())
                .contains(primero.getNombre());
        mvc.perform(post("/roles/" + primero.getIdRol() + "/permisos").session(sesion(admin)).with(csrf()))
                .andExpect(flash().attributeExists("exito"));
        em.flush();
        em.clear();
        assertThat(permisos.findById(editar.getIdPermiso()).orElseThrow().getRoles()).isEmpty();
        assertThat(roles.existsById(primero.getIdRol())).isTrue();
        assertThat(permisos.existsById(ver.getIdPermiso())).isTrue();
    }

    @Test
    void elEntityGraphPrecargaRolesYNoDuplicaPermisos() {
        List<Permiso> lista = service.listarPermisos();
        em.clear();
        assertThat(lista).hasSize(2).extracting(Permiso::getNombre).containsExactly("EDITAR", "VER");
        assertThat(lista.get(1).getRoles()).hasSize(2);
    }

    @Test
    void laPantallaNoEstaDisponibleParaUnNoAdministrador() throws Exception {
        mvc.perform(get("/roles/" + primero.getIdRol() + "/permisos").session(sesion(lector)))
                .andExpect(redirectedUrl("/roles"));
    }

    @Test
    void unaPeticionSinCsrfNoRevocaLasRelaciones() throws Exception {
        mvc.perform(post("/roles/" + primero.getIdRol() + "/permisos").session(sesion(admin)))
                .andExpect(status().isForbidden());
        assertThat(permisos.findById(ver.getIdPermiso()).orElseThrow().getRoles()).hasSize(2);
    }

    private Permiso permiso(String nombre) {
        Permiso permiso = new Permiso();
        permiso.setNombre(nombre);
        permiso.setModulo("usuarios");
        return permisos.saveAndFlush(permiso);
    }

    private Rol rol(String nombre, Permiso... asignados) {
        Rol rol = new Rol();
        rol.setNombre(nombre);
        rol.setArea("Medicina");
        rol.setPermisos(new LinkedHashSet<>(List.of(asignados)));
        return roles.saveAndFlush(rol);
    }

    private Usuario usuario(String nombre, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setUsuario(nombre);
        usuario.setCorreo(nombre + "@pruebas.local");
        usuario.setNombres("Prueba");
        usuario.setApellidos("Relación inversa");
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
