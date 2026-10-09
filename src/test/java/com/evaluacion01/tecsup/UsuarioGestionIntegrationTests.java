package com.evaluacion01.tecsup;

import com.evaluacion01.tecsup.entity.Rol;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.RolRepository;
import com.evaluacion01.tecsup.repository.UsuarioRepository;
import com.evaluacion01.tecsup.service.UsuarioService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

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

// Backend de usuarios: relación Usuario–Rol principal (FK usuarios.id_rol), registro,
// listado, búsqueda, edición, activación/desactivación, sistema multirrol, validaciones
// y protecciones de cuentas administrativas.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UsuarioGestionIntegrationTests {

    @Autowired private MockMvc mvc;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private RolRepository roles;
    @Autowired private UsuarioService usuarioService;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;

    private Rol administrador;
    private Rol medicoGeneral;
    private Rol medicoEspecialista;
    private Rol enfermero;
    private Usuario admin;

    @BeforeEach
    void prepararDatos() {
        administrador = rol("Administrador", "Administración");
        medicoGeneral = rol("Médico general", "Medicina");
        medicoEspecialista = rol("Médico especialista", "Medicina");
        enfermero = rol("Enfermero", "Enfermería");
        admin = usuario("adminPrueba", administrador);
        usuarios.flush();
    }

    // ---- Pregunta 1: relación Usuario–Rol principal ----

    @Test
    void registrarGuardaElRolPrincipalEnLaClaveForaneaIdRol() throws Exception {
        mvc.perform(alta("ana", medicoGeneral).session(sesion(admin)))
                .andExpect(redirectedUrl("/usuarios"))
                .andExpect(flash().attribute("exito", "Usuario registrado correctamente"));

        Usuario ana = recargar("ana");
        assertThat(ana.getRol().getIdRol()).isEqualTo(medicoGeneral.getIdRol());
        assertThat(idRolEnTabla(ana)).isEqualTo(medicoGeneral.getIdRol());
        assertThat(idsRoles(ana)).containsExactly(medicoGeneral.getIdRol());
    }

    @Test
    void editarCambiaElRolPrincipalEnLaClaveForanea() throws Exception {
        Usuario ana = usuario("ana", medicoGeneral);

        mvc.perform(edicion(ana, medicoEspecialista).session(sesion(admin)))
                .andExpect(flash().attribute("exito", "Usuario actualizado correctamente"));

        Usuario editada = recargar("ana");
        assertThat(editada.getRol().getIdRol()).isEqualTo(medicoEspecialista.getIdRol());
        assertThat(idRolEnTabla(editada)).isEqualTo(medicoEspecialista.getIdRol());
    }

    @Test
    void laClaveForaneaRechazaUnRolInexistente() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO usuarios (nombres, apellidos, correo, usuario, contrasena, estado, id_rol)
                VALUES ('X', 'Y', 'x@pruebas.local', 'sinrol', 'hash', TRUE, 999999)
                """)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void laClaveForaneaNoPermiteBorrarUnRolEnUso() {
        Integer idRol = medicoGeneral.getIdRol();
        usuario("ana", medicoGeneral);
        usuarios.flush();
        assertThatThrownBy(() -> jdbc.update("DELETE FROM roles WHERE id_rol = ?", idRol))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void elSqlDelProyectoDeclaraLaClaveForaneaYLaTablaMultirrol() throws Exception {
        String schema = new ClassPathResource("schema.sql").getContentAsString(StandardCharsets.UTF_8);
        assertThat(schema).containsIgnoringWhitespaces(
                "CONSTRAINT fk_usuario_rol FOREIGN KEY (id_rol) REFERENCES roles (id_rol)");
        assertThat(schema).contains("CREATE TABLE IF NOT EXISTS usuario_roles");
        assertThat(schema).containsIgnoringWhitespaces(
                "INSERT IGNORE INTO usuario_roles (id_usuario, id_rol) SELECT id_usuario, id_rol FROM usuarios");
    }

    // ---- Pregunta 3: gestión de usuarios ----

    @Test
    void registraConRolesAdicionalesDeSuAreaYSiempreIncluyeElPrincipal() throws Exception {
        mvc.perform(alta("ana", medicoGeneral)
                        .param("idsRoles", medicoEspecialista.getIdRol().toString())
                        .param("idsRoles", medicoGeneral.getIdRol().toString())
                        .session(sesion(admin)))
                .andExpect(flash().attribute("exito", "Usuario registrado correctamente"));

        Usuario ana = recargar("ana");
        assertThat(ana.getRol().getIdRol()).isEqualTo(medicoGeneral.getIdRol());
        assertThat(idsRoles(ana)).containsExactlyInAnyOrder(medicoGeneral.getIdRol(), medicoEspecialista.getIdRol());
        assertThat(ana.getRolesAsignados().iterator().next().getIdRol()).isEqualTo(medicoGeneral.getIdRol());
        assertThat(encoder.matches("claveSegura", ana.getContrasena())).isTrue();
        assertThat(ana.getEstado()).isTrue();
    }

    @Test
    void editarPuedeQuitarRolesAdicionalesSinPerderElPrincipal() throws Exception {
        Usuario ana = usuario("ana", medicoGeneral);
        ana.getRoles().add(medicoEspecialista);
        usuarios.saveAndFlush(ana);

        mvc.perform(edicion(ana, medicoGeneral).session(sesion(admin)));

        assertThat(idsRoles(recargar("ana"))).containsExactly(medicoGeneral.getIdRol());
    }

    @Test
    void listaYBuscaPorTextoAreaRolAdicionalYEstado() {
        Usuario ana = usuario("ana", medicoGeneral);
        ana.setDni("12345678");
        ana.getRoles().add(medicoEspecialista);
        Usuario luis = usuario("luis", enfermero);
        luis.setEstado(false);
        usuarios.saveAndFlush(ana);
        usuarios.saveAndFlush(luis);

        assertThat(nombres(usuarioService.buscar(null, null, null, null)))
                .containsExactlyInAnyOrder("adminPrueba", "ana", "luis");
        assertThat(nombres(usuarioService.buscar("  ANA ", null, null, null))).containsExactly("ana");
        assertThat(nombres(usuarioService.buscar("1234", null, null, null))).containsExactly("ana");
        assertThat(nombres(usuarioService.buscar("luis@pruebas", null, null, null))).containsExactly("luis");
        assertThat(nombres(usuarioService.buscar(null, "Medicina", null, null))).containsExactly("ana");
        assertThat(nombres(usuarioService.buscar(null, null, medicoEspecialista.getIdRol(), null))).containsExactly("ana");
        assertThat(nombres(usuarioService.buscar(null, null, null, false))).containsExactly("luis");
        assertThat(nombres(usuarioService.buscar("ana", null, null, false))).isEmpty();
    }

    @Test
    void laVistaDeUsuariosAplicaLosFiltros() throws Exception {
        usuario("ana", medicoGeneral);
        mvc.perform(get("/usuarios").param("q", "ana").session(sesion(admin)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("hayFiltros", true))
                .andExpect(model().attribute("listaUsuarios",
                        org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void editarActualizaDatosSinCambiarEstadoNiContrasenaSiVieneVacia() throws Exception {
        Usuario ana = usuario("ana", medicoGeneral);
        ana.setEstado(false);
        usuarios.saveAndFlush(ana);
        String hashAnterior = ana.getContrasena();

        mvc.perform(edicion(ana, medicoGeneral, "  Ana María ").session(sesion(admin)))
                .andExpect(flash().attribute("exito", "Usuario actualizado correctamente"));

        Usuario editada = recargar("ana");
        assertThat(editada.getNombres()).isEqualTo("Ana María");
        assertThat(editada.getEstado()).isFalse();
        assertThat(editada.getContrasena()).isEqualTo(hashAnterior);
    }

    @Test
    void activarYDesactivarSonExplicitosEIdempotentes() throws Exception {
        Usuario ana = usuario("ana", medicoGeneral);
        MockHttpSession sesion = sesion(admin);

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/usuarios/{id}/estado", ana.getIdUsuario()).with(csrf())
                    .param("activo", "false").session(sesion));
            assertThat(recargar("ana").getEstado()).isFalse();
        }
        mvc.perform(post("/usuarios/{id}/estado", ana.getIdUsuario()).with(csrf())
                .param("activo", "true").session(sesion));
        assertThat(recargar("ana").getEstado()).isTrue();
    }

    @Test
    void validaCamposObligatorios() {
        Usuario sinNombre = datos("ana", medicoGeneral);
        sinNombre.setNombres("   ");
        assertThatThrownBy(() -> usuarioService.registrarUsuario(sinNombre, null, admin))
                .hasMessage("El campo nombres es obligatorio.");

        Usuario sinCorreo = datos("ana", medicoGeneral);
        sinCorreo.setCorreo(null);
        assertThatThrownBy(() -> usuarioService.registrarUsuario(sinCorreo, null, admin))
                .hasMessage("El campo correo es obligatorio.");

        Usuario sinClave = datos("ana", medicoGeneral);
        sinClave.setContrasena("");
        assertThatThrownBy(() -> usuarioService.registrarUsuario(sinClave, null, admin))
                .hasMessage("La contraseña es obligatoria para registrar un usuario.");

        Usuario sinRol = datos("ana", medicoGeneral);
        sinRol.setRol(new Rol());
        assertThatThrownBy(() -> usuarioService.registrarUsuario(sinRol, null, admin))
                .hasMessage("Debes seleccionar un rol principal.");

        assertThat(usuarios.findByUsuario("ana")).isEmpty();
    }

    @Test
    void validaFormatosDeCorreoDniTelefonoUsuarioYContrasena() {
        assertRechazo(u -> u.setCorreo("no-es-correo"), "El correo no tiene un formato válido.");
        assertRechazo(u -> u.setDni("1234"), "El DNI debe tener 8 dígitos.");
        assertRechazo(u -> u.setTelefono("abc"), "El teléfono solo puede tener dígitos, espacios y un + inicial.");
        assertRechazo(u -> u.setUsuario("a b"),
                "El usuario debe tener de 3 a 50 caracteres: letras, números, punto, guion o guion bajo.");
        assertRechazo(u -> u.setContrasena("123"), "La contraseña debe tener al menos 6 caracteres.");
    }

    @Test
    void rechazaDuplicadosSinDistinguirMayusculas() {
        Usuario ana = usuario("ana", medicoGeneral);
        ana.setDni("12345678");
        usuarios.saveAndFlush(ana);

        assertRechazo(u -> u.setUsuario("ANA"), "Ya existe un usuario con ese nombre de usuario.");
        assertRechazo(u -> u.setCorreo("ANA@pruebas.local"), "Ya existe un usuario con ese correo.");
        assertRechazo(u -> u.setDni("12345678"), "Ya existe un usuario con ese DNI.");
    }

    @Test
    void alEditarConservarLosPropiosDatosNoCuentaComoDuplicadoPeroTomarLosDeOtroSi() {
        Usuario ana = usuario("ana", medicoGeneral);
        usuario("luis", enfermero);

        Usuario mismosDatos = datos("ana", medicoGeneral);
        mismosDatos.setContrasena("");
        usuarioService.actualizarUsuario(ana.getIdUsuario(), mismosDatos, null, admin);

        Usuario correoAjeno = datos("ana", medicoGeneral);
        correoAjeno.setCorreo("luis@pruebas.local");
        correoAjeno.setContrasena("");
        assertThatThrownBy(() -> usuarioService.actualizarUsuario(ana.getIdUsuario(), correoAjeno, null, admin))
                .hasMessage("Ya existe un usuario con ese correo.");
    }

    @Test
    void dniYTelefonoVaciosSeGuardanComoNullYNoChocanEntreSi() {
        Usuario uno = datos("ana", medicoGeneral);
        uno.setDni("");
        uno.setTelefono("  ");
        Usuario dos = datos("luis", medicoGeneral);
        dos.setDni("");

        usuarioService.registrarUsuario(uno, null, admin);
        usuarioService.registrarUsuario(dos, null, admin);

        assertThat(recargar("ana").getDni()).isNull();
        assertThat(recargar("ana").getTelefono()).isNull();
        assertThat(recargar("luis").getDni()).isNull();
    }

    @Test
    void elFormularioMuestraElMensajeDeDuplicado() throws Exception {
        usuario("ana", medicoGeneral);
        mvc.perform(alta("ana", medicoGeneral).session(sesion(admin)))
                .andExpect(flash().attribute("error", "Ya existe un usuario con ese nombre de usuario."));
    }

    @Test
    void rechazaRolesInexistentesODeOtraArea() {
        assertThatThrownBy(() -> usuarioService.registrarUsuario(
                datos("ana", medicoGeneral), List.of(enfermero.getIdRol()), admin))
                .hasMessage("El rol 'Enfermero' no corresponde al área elegida.");
        assertThatThrownBy(() -> usuarioService.registrarUsuario(
                datos("ana", medicoGeneral), List.of(999999), admin))
                .hasMessage("Rol no encontrado: 999999");
        Usuario areaDistinta = datos("ana", medicoGeneral);
        areaDistinta.setArea("Enfermería");
        assertThatThrownBy(() -> usuarioService.registrarUsuario(areaDistinta, null, admin))
                .hasMessage("El rol 'Médico general' no corresponde al área elegida.");
        assertThat(usuarios.findByUsuario("ana")).isEmpty();
    }

    // ---- Protecciones de cuentas administrativas ----

    @Test
    void nadiePuedeDesactivarSuPropiaCuenta() {
        assertThatThrownBy(() -> usuarioService.cambiarEstado(admin.getIdUsuario(), false, admin))
                .hasMessage("No puedes desactivar tu propia cuenta.");
        assertThat(recargar("adminPrueba").getEstado()).isTrue();
    }

    @Test
    void noSePuedeDesactivarAlUltimoAdministradorActivo() {
        Usuario otroAdmin = usuario("adminDos", administrador);
        usuarioService.cambiarEstado(otroAdmin.getIdUsuario(), false, admin);

        // Con un solo administrador activo, otro administrador (inactivo) no puede dejarlo sin acceso.
        assertThatThrownBy(() -> usuarioService.cambiarEstado(admin.getIdUsuario(), false, otroAdmin))
                .isInstanceOf(RuntimeException.class);
        assertThat(recargar("adminPrueba").getEstado()).isTrue();
    }

    @Test
    void noSePuedeQuitarElRolAdministradorAlUltimoAdministrador() {
        Usuario cambio = datos("adminPrueba", administrador);
        cambio.setArea("Medicina");
        cambio.setRol(medicoGeneral);
        cambio.setContrasena("");
        Usuario otroAdmin = usuario("adminDos", administrador);
        otroAdmin.setEstado(false);
        usuarios.saveAndFlush(otroAdmin);

        assertThatThrownBy(() -> usuarioService.actualizarUsuario(admin.getIdUsuario(), cambio, null, otroAdmin))
                .isInstanceOf(RuntimeException.class);
        assertThat(recargar("adminPrueba").getRol().getIdRol()).isEqualTo(administrador.getIdRol());
    }

    // ---- utilidades ----

    private void assertRechazo(java.util.function.Consumer<Usuario> cambio, String mensaje) {
        Usuario nuevo = datos("nuevo", medicoGeneral);
        cambio.accept(nuevo);
        assertThatThrownBy(() -> usuarioService.registrarUsuario(nuevo, null, admin)).hasMessage(mensaje);
    }

    private MockHttpServletRequestBuilder alta(String nombreUsuario, Rol principal) {
        return post("/usuarios/guardar").with(csrf())
                .param("nombres", "Nombre").param("apellidos", "Prueba")
                .param("correo", nombreUsuario + "@pruebas.local").param("usuario", nombreUsuario)
                .param("contrasena", "claveSegura")
                .param("area", principal.getArea()).param("rol.idRol", principal.getIdRol().toString());
    }

    private MockHttpServletRequestBuilder edicion(Usuario existente, Rol principal) {
        return edicion(existente, principal, existente.getNombres());
    }

    private MockHttpServletRequestBuilder edicion(Usuario existente, Rol principal, String nombres) {
        return post("/usuarios/guardar").with(csrf())
                .param("idUsuario", existente.getIdUsuario().toString())
                .param("nombres", nombres).param("apellidos", existente.getApellidos())
                .param("correo", existente.getCorreo()).param("usuario", existente.getUsuario())
                .param("contrasena", "")
                .param("area", principal.getArea()).param("rol.idRol", principal.getIdRol().toString());
    }

    private Usuario datos(String nombreUsuario, Rol principal) {
        Usuario usuario = new Usuario();
        usuario.setNombres("Nombre");
        usuario.setApellidos("Prueba");
        usuario.setUsuario(nombreUsuario);
        usuario.setCorreo(nombreUsuario + "@pruebas.local");
        usuario.setContrasena("claveSegura");
        usuario.setArea(principal.getArea());
        Rol referencia = new Rol();
        referencia.setIdRol(principal.getIdRol());
        usuario.setRol(referencia);
        return usuario;
    }

    private Usuario recargar(String nombreUsuario) {
        em.flush();
        em.clear();
        return usuarios.findByUsuario(nombreUsuario).orElseThrow();
    }

    private Integer idRolEnTabla(Usuario usuario) {
        return jdbc.queryForObject("SELECT id_rol FROM usuarios WHERE id_usuario = ?", Integer.class,
                usuario.getIdUsuario());
    }

    private List<Integer> idsRoles(Usuario usuario) {
        return usuario.getRoles().stream().map(Rol::getIdRol).toList();
    }

    private List<String> nombres(List<Usuario> lista) {
        return lista.stream().map(Usuario::getUsuario).toList();
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
        usuario.setContrasena(encoder.encode("claveInicial"));
        usuario.setArea(principal.getArea());
        usuario.setRol(principal);
        usuario.setRoles(new LinkedHashSet<>(Set.of(principal)));
        return usuarios.saveAndFlush(usuario);
    }
}
