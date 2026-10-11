package com.evaluacion01.tecsup;

import com.evaluacion01.tecsup.entity.Permiso;
import com.evaluacion01.tecsup.entity.Rol;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.PermisoRepository;
import com.evaluacion01.tecsup.repository.RolRepository;
import com.evaluacion01.tecsup.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Pregunta 1: relación Rol–Permiso desde el lado principal (Rol.permisos, @ManyToMany)
// y su tabla intermedia rol_permisos con las claves foráneas id_rol e id_permiso.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RolPermisoRelacionIntegrationTests {

    @Autowired private MockMvc mvc;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private RolRepository roles;
    @Autowired private PermisoRepository permisos;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;

    private Permiso verUsuarios;
    private Permiso crearUsuarios;
    private Permiso editarUsuarios;
    private Rol administrador;
    private Rol recepcionista;
    private Usuario admin;

    @BeforeEach
    void prepararDatos() {
        verUsuarios = permiso("usuarios", "VER");
        crearUsuarios = permiso("usuarios", "CREAR");
        editarUsuarios = permiso("usuarios", "EDITAR");
        administrador = rol("Administrador", "Administración");
        recepcionista = rol("Recepcionista", "Recepción");
        admin = usuario("adminPrueba", administrador);
        usuarios.flush();
    }

    // ---- Anotaciones JPA y SQL ----

    @Test
    void rolEsElLadoPrincipalDeLaRelacionConLaTablaRolPermisos() throws Exception {
        Field campo = Rol.class.getDeclaredField("permisos");
        JoinTable tabla = campo.getAnnotation(JoinTable.class);

        assertThat(campo.getAnnotation(ManyToMany.class).mappedBy()).isEmpty();
        assertThat(tabla.name()).isEqualTo("rol_permisos");
        assertThat(tabla.joinColumns()[0].name()).isEqualTo("id_rol");
        assertThat(tabla.inverseJoinColumns()[0].name()).isEqualTo("id_permiso");
    }

    @Test
    void elSqlDelProyectoDeclaraLaTablaIntermediaYSusClavesForaneas() throws Exception {
        String schema = new ClassPathResource("schema.sql").getContentAsString(StandardCharsets.UTF_8);
        assertThat(schema).contains("CREATE TABLE IF NOT EXISTS rol_permisos");
        assertThat(schema).containsIgnoringWhitespaces("PRIMARY KEY (id_rol, id_permiso)");
        assertThat(schema).containsIgnoringWhitespaces(
                "CONSTRAINT fk_rolpermiso_rol FOREIGN KEY (id_rol) REFERENCES roles (id_rol)");
        assertThat(schema).containsIgnoringWhitespaces(
                "CONSTRAINT fk_rolpermiso_permiso FOREIGN KEY (id_permiso) REFERENCES permisos (id_permiso)");
    }

    // ---- Asignación, consulta, modificación y revocación ----

    @Test
    void asignarPermisosCreaLasFilasEnRolPermisos() throws Exception {
        asignar(recepcionista, verUsuarios, crearUsuarios)
                .andExpect(flash().attribute("exito", "Permisos actualizados correctamente"));

        assertThat(idsPermisosEnTabla(recepcionista))
                .containsExactlyInAnyOrder(verUsuarios.getIdPermiso(), crearUsuarios.getIdPermiso());
    }

    @Test
    void consultarElRolDevuelveSusPermisosDesdeLaBaseDeDatos() throws Exception {
        asignar(recepcionista, verUsuarios, crearUsuarios);
        em.flush();
        em.clear();

        Rol recargado = roles.findById(recepcionista.getIdRol()).orElseThrow();
        assertThat(recargado.getPermisos()).extracting(Permiso::getNombre)
                .containsExactlyInAnyOrder("VER", "CREAR");
        mvc.perform(get("/roles/" + recepcionista.getIdRol() + "/permisos").session(sesion(admin)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("idsAsignados",
                        Set.of(verUsuarios.getIdPermiso(), crearUsuarios.getIdPermiso())));
    }

    @Test
    void modificarReemplazaElConjuntoDePermisosDelRol() throws Exception {
        asignar(recepcionista, verUsuarios, crearUsuarios);
        asignar(recepcionista, verUsuarios, editarUsuarios);

        assertThat(idsPermisosEnTabla(recepcionista))
                .containsExactlyInAnyOrder(verUsuarios.getIdPermiso(), editarUsuarios.getIdPermiso());
    }

    @Test
    void revocarQuitaLasFilasPeroConservaElRolYLosPermisosDelCatalogo() throws Exception {
        asignar(recepcionista, verUsuarios, crearUsuarios);
        asignar(recepcionista);

        assertThat(idsPermisosEnTabla(recepcionista)).isEmpty();
        assertThat(roles.findById(recepcionista.getIdRol())).isPresent();
        assertThat(permisos.count()).isEqualTo(3);
    }

    @Test
    void unMismoPermisoPuedePertenecerAVariosRoles() throws Exception {
        asignar(recepcionista, verUsuarios);
        asignar(administrador, verUsuarios);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rol_permisos WHERE id_permiso = ?",
                Integer.class, verUsuarios.getIdPermiso())).isEqualTo(2);
    }

    // ---- Claves foráneas de rol_permisos ----

    @Test
    void laClaveForaneaRechazaUnRolOUnPermisoInexistente() {
        roles.flush();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO rol_permisos (id_rol, id_permiso) VALUES (?, ?)",
                999999, verUsuarios.getIdPermiso())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO rol_permisos (id_rol, id_permiso) VALUES (?, ?)",
                recepcionista.getIdRol(), 999999)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void laClavePrimariaCompuestaNoPermiteRepetirLaMismaAsignacion() throws Exception {
        asignar(recepcionista, verUsuarios);
        roles.flush();

        assertThatThrownBy(() -> jdbc.update("INSERT INTO rol_permisos (id_rol, id_permiso) VALUES (?, ?)",
                recepcionista.getIdRol(), verUsuarios.getIdPermiso()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private org.springframework.test.web.servlet.ResultActions asignar(Rol rol, Permiso... asignados) throws Exception {
        var request = post("/roles/" + rol.getIdRol() + "/permisos").session(sesion(admin)).with(csrf());
        for (Permiso permiso : asignados) {
            request.param("permisoIds", permiso.getIdPermiso().toString());
        }
        return mvc.perform(request);
    }

    private List<Integer> idsPermisosEnTabla(Rol rol) {
        roles.flush();
        return jdbc.queryForList("SELECT id_permiso FROM rol_permisos WHERE id_rol = ?", Integer.class, rol.getIdRol());
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
