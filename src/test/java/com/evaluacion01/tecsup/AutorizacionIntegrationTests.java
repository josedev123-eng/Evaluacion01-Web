package com.evaluacion01.tecsup;

import com.evaluacion01.tecsup.entity.Permiso;
import com.evaluacion01.tecsup.entity.Rol;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.PermisoRepository;
import com.evaluacion01.tecsup.repository.RolRepository;
import com.evaluacion01.tecsup.repository.UsuarioRepository;
import com.evaluacion01.tecsup.service.AutorizacionService;
import com.evaluacion01.tecsup.service.RolService;
import com.evaluacion01.tecsup.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AutorizacionIntegrationTests {

    @Autowired private MockMvc mvc;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private RolRepository roles;
    @Autowired private PermisoRepository permisos;
    @Autowired private AutorizacionService autorizacion;
    @Autowired private UsuarioService usuarioService;
    @Autowired private RolService rolService;
    @Autowired private PasswordEncoder encoder;

    private Permiso verUsuarios;
    private Permiso crearUsuarios;
    private Permiso editarUsuarios;
    private Permiso verRoles;
    private Permiso editarRoles;
    private Rol administrador;
    private Rol coordinador;
    private Rol auditor;
    private Rol medico;
    private Usuario admin;
    private Usuario operador;
    private Usuario lector;
    private String hashInicial;

    @BeforeEach
    void prepararDatos() {
        verUsuarios = permiso("usuarios", "VER");
        crearUsuarios = permiso("usuarios", "CREAR");
        editarUsuarios = permiso("usuarios", "EDITAR");
        verRoles = permiso("roles", "VER");
        editarRoles = permiso("roles", "EDITAR");
        administrador = rol("Administrador", "Administración");
        coordinador = rol("Coordinador", "Administración", verUsuarios, crearUsuarios, editarUsuarios, verRoles);
        auditor = rol("Auditor", "Administración", verUsuarios, verRoles);
        medico = rol("Médico", "Medicina");
        hashInicial = encoder.encode("claveInicial");
        admin = usuario("adminPrueba", administrador);
        operador = usuario("operador", coordinador);
        lector = usuario("lector", auditor);
        usuarios.flush();
    }

    @Test
    void permisosAdicionalesHabilitanLasAccionesYLaNavegacion() throws Exception {
        lector.getRoles().add(coordinador);
        usuarios.saveAndFlush(lector);

        mvc.perform(get("/usuarios").session(sesion(lector)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("puedeCrear", true))
                .andExpect(model().attribute("puedeEditar", true))
                .andExpect(model().attribute("puedeVerRoles", true))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        assertThat(lector.getRol()).isEqualTo(auditor);
    }

    @Test
    void unUsuarioSinPermisosNoPuedeEntrarPorUrlDirecta() throws Exception {
        Usuario clinico = usuario("clinico", medico);
        mvc.perform(get("/usuarios").session(sesion(clinico)))
                .andExpect(redirectedUrl("/dashboard"));
        mvc.perform(get("/roles").session(sesion(clinico)))
                .andExpect(redirectedUrl("/dashboard"));
        mvc.perform(formulario(lector, auditor).session(sesion(clinico)))
                .andExpect(flash().attributeExists("error"));
        assertThat(lector.getNombres()).isEqualTo("Nombre");
    }

    @Test
    void noPermiteConsultarNiGuardarUsuariosSinSesion() throws Exception {
        mvc.perform(get("/usuarios")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/roles/nuevo")).andExpect(redirectedUrl("/login"));
        mvc.perform(formulario(null, coordinador)).andExpect(redirectedUrl("/login"));
        assertThat(usuarios.findByUsuario("nuevo")).isEmpty();
    }

    @Test
    void unLectorNoPuedeEditarAunqueEnvieElFormularioManualmente() throws Exception {
        mvc.perform(formulario(operador, coordinador).session(sesion(lector)))
                .andExpect(flash().attributeExists("error"));
        assertThat(operador.getNombres()).isEqualTo("Nombre");
    }

    @Test
    void editarSinVerNoPermiteConsultarElListadoPorLaRutaDeEdicion() throws Exception {
        Rol soloEdicion = rol("Editor sin consulta", "Administración", editarUsuarios);
        operador.setRol(soloEdicion);
        operador.setRoles(new LinkedHashSet<>(Set.of(soloEdicion)));
        usuarios.saveAndFlush(operador);
        mvc.perform(get("/usuarios/editar/" + operador.getIdUsuario()).session(sesion(operador)))
                .andExpect(redirectedUrl("/usuarios"));
    }

    @Test
    void impideAutoasignarAdministradorComoRolPrincipal() throws Exception {
        mvc.perform(formulario(operador, administrador).session(sesion(operador)))
                .andExpect(flash().attributeExists("error"));
        assertThat(operador.getRol()).isEqualTo(coordinador);
        assertThat(autorizacion.esAdministrador(operador)).isFalse();
    }

    @Test
    void impideAutoasignarAdministradorComoRolAdicional() throws Exception {
        mvc.perform(formulario(operador, coordinador).session(sesion(operador))
                        .param("idsRoles", administrador.getIdRol().toString()))
                .andExpect(flash().attributeExists("error"));
        assertThat(operador.getRoles()).containsExactly(coordinador);
    }

    @Test
    void impideCrearUnaCuentaAlternativaAdministradora() throws Exception {
        mvc.perform(formulario(null, administrador).session(sesion(operador)))
                .andExpect(flash().attributeExists("error"));
        assertThat(usuarios.findByUsuario("nuevo")).isEmpty();
    }

    @Test
    void impideCrearUnaCuentaAlternativaConAdministradorAdicional() throws Exception {
        mvc.perform(formulario(null, coordinador).session(sesion(operador))
                        .param("idsRoles", administrador.getIdRol().toString()))
                .andExpect(flash().attributeExists("error"));
        assertThat(usuarios.findByUsuario("nuevo")).isEmpty();
    }

    @Test
    void unOperadorNoPuedeLeerElFormularioNiCambiarLaClaveDeUnAdministrador() throws Exception {
        mvc.perform(get("/usuarios/editar/" + admin.getIdUsuario()).session(sesion(operador)))
                .andExpect(redirectedUrl("/usuarios"))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(formulario(admin, administrador).session(sesion(operador))
                        .param("contrasena", "claveManipulada"))
                .andExpect(flash().attributeExists("error"));
        assertThat(admin.getContrasena()).isEqualTo(hashInicial);
        assertThat(admin.getNombres()).isEqualTo("Nombre");
    }

    @Test
    void protegeUnaCuentaAdministradoraAunqueAdministradorSeaAdicional() throws Exception {
        lector.getRoles().add(administrador);
        usuarios.saveAndFlush(lector);
        mvc.perform(formulario(lector, auditor).session(sesion(operador)))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/usuarios/" + lector.getIdUsuario() + "/estado")
                        .session(sesion(operador)).with(csrf()).param("activo", "false"))
                .andExpect(flash().attributeExists("error"));
        assertThat(lector.getEstado()).isTrue();
        assertThat(lector.getRoles()).contains(administrador);
    }

    @Test
    void unOperadorNoPuedeDesactivarUnaCuentaAdministradora() throws Exception {
        mvc.perform(post("/usuarios/" + admin.getIdUsuario() + "/estado")
                        .session(sesion(operador)).with(csrf()).param("activo", "false"))
                .andExpect(flash().attributeExists("error"));
        assertThat(admin.getEstado()).isTrue();
    }

    @Test
    void soloAdministradorGestionaRolesInclusoSiOtroRolTieneEditarRoles() throws Exception {
        coordinador.getPermisos().add(editarRoles);
        roles.saveAndFlush(coordinador);
        for (String ruta : List.of("/roles/nuevo", "/roles/" + coordinador.getIdRol() + "/editar",
                "/roles/" + coordinador.getIdRol() + "/permisos")) {
            mvc.perform(get(ruta).session(sesion(operador))).andExpect(redirectedUrl("/roles"));
        }
        mvc.perform(post("/roles/guardar").session(sesion(operador)).with(csrf())
                        .param("idRol", coordinador.getIdRol().toString())
                        .param("nombre", "Administrador").param("area", "Administración"))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/roles/" + coordinador.getIdRol() + "/permisos")
                        .session(sesion(operador)).with(csrf()).param("permisoIds", editarUsuarios.getIdPermiso().toString()))
                .andExpect(flash().attributeExists("error"));
        assertThat(coordinador.getNombre()).isEqualTo("Coordinador");
        assertThat(coordinador.getPermisos()).contains(verUsuarios, crearUsuarios, editarRoles);
    }

    @Test
    void rechazaOperacionesSinTokenCsrfAunqueLaSesionSeaAdministradora() throws Exception {
        mvc.perform(post("/roles/guardar").session(sesion(admin))
                        .param("nombre", "Rol forjado").param("area", "Administración"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/usuarios/guardar").session(sesion(admin))
                        .param("idUsuario", lector.getIdUsuario().toString()).param("nombres", "Forjado"))
                .andExpect(status().isForbidden());
        assertThat(roles.findByNombreIgnoreCase("Rol forjado")).isEmpty();
        assertThat(lector.getNombres()).isEqualTo("Nombre");
    }

    @Test
    void administradorPuedeAsignarAdministradorAdicionalYLaCuentaObtieneAcceso() throws Exception {
        mvc.perform(formulario(null, coordinador).session(sesion(admin))
                        .param("idsRoles", administrador.getIdRol().toString()))
                .andExpect(flash().attributeExists("exito"));
        Usuario nuevo = usuarios.findByUsuario("nuevo").orElseThrow();
        assertThat(nuevo.getRol()).isEqualTo(coordinador);
        assertThat(nuevo.getRoles()).contains(coordinador, administrador);
        assertThat(autorizacion.esAdministrador(nuevo)).isTrue();
        assertThat(autorizacion.tienePermiso(nuevo, "roles", "EDITAR")).isTrue();
        assertThat(encoder.matches("claveNueva", nuevo.getContrasena())).isTrue();
    }

    @Test
    void noSePuedeQuitarAdministradorPrincipalAlUltimoAdministradorActivo() throws Exception {
        mvc.perform(formulario(admin, coordinador).session(sesion(admin)))
                .andExpect(flash().attributeExists("error"));
        assertThat(admin.getRol()).isEqualTo(administrador);
        assertThat(admin.getNombres()).isEqualTo("Nombre");
    }

    @Test
    void noSePuedeQuitarAdministradorAdicionalAlUltimoAdministradorActivo() throws Exception {
        admin.setEstado(false);
        operador.getRoles().add(administrador);
        usuarios.saveAndFlush(admin);
        usuarios.saveAndFlush(operador);

        assertThat(usuarios.contarActivosConRol("Administrador")).isEqualTo(1);
        mvc.perform(formulario(operador, coordinador).session(sesion(operador)))
                .andExpect(flash().attributeExists("error"));
        assertThat(operador.getRoles()).contains(administrador);
    }

    @Test
    void permiteQuitarUnAdministradorCuandoOtroTieneAdministradorAdicional() throws Exception {
        operador.getRoles().add(administrador);
        usuarios.saveAndFlush(operador);
        assertThat(usuarios.contarActivosConRol("Administrador")).isEqualTo(2);

        mvc.perform(formulario(admin, coordinador).session(sesion(admin)))
                .andExpect(flash().attributeExists("exito"));
        usuarios.flush();
        assertThat(admin.getRol()).isEqualTo(coordinador);
        assertThat(usuarios.contarActivosConRol("Administrador")).isEqualTo(1);
    }

    @Test
    void puedeDesactivarOtroAdministradorAdicionalSiQuedaUnoActivo() throws Exception {
        operador.getRoles().add(administrador);
        usuarios.saveAndFlush(operador);
        mvc.perform(post("/usuarios/" + operador.getIdUsuario() + "/estado")
                        .session(sesion(admin)).with(csrf()).param("activo", "false"))
                .andExpect(flash().attributeExists("exito"));
        assertThat(operador.getEstado()).isFalse();
        assertThat(usuarios.contarActivosConRol("Administrador")).isEqualTo(1);
    }

    @Test
    void noPermiteModificarLosPropiosRolesAunqueElNuevoRolNoSeaAdministrador() throws Exception {
        mvc.perform(formulario(operador, coordinador).session(sesion(operador))
                        .param("idsRoles", auditor.getIdRol().toString()))
                .andExpect(flash().attributeExists("error"));
        assertThat(operador.getRoles()).containsExactly(coordinador);
    }

    @Test
    void permiteEditarLosPropiosDatosSinCambiarLosRoles() throws Exception {
        mvc.perform(formulario(operador, coordinador).session(sesion(operador)))
                .andExpect(flash().attributeExists("exito"));
        assertThat(operador.getNombres()).isEqualTo("Editado");
        assertThat(operador.getRol()).isEqualTo(coordinador);
    }

    @Test
    void noPermiteCrearNiTomarCuentasConPermisosSuperiores() throws Exception {
        Permiso especial = permiso("usuarios", "SUPERVISAR");
        Rol superior = rol("Supervisor", "Administración", especial);
        Usuario supervisor = usuario("supervisor", superior);

        mvc.perform(formulario(null, superior).session(sesion(operador)))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(formulario(supervisor, superior).session(sesion(operador))
                        .param("contrasena", "claveManipulada"))
                .andExpect(flash().attributeExists("error"));
        assertThat(usuarios.findByUsuario("nuevo")).isEmpty();
        assertThat(supervisor.getContrasena()).isEqualTo(hashInicial);
    }

    @Test
    void ignoraCamposSensiblesYNoReactivaUnUsuarioAlEditar() throws Exception {
        lector.setEstado(false);
        usuarios.saveAndFlush(lector);
        mvc.perform(formulario(lector, auditor).session(sesion(operador))
                        .param("estado", "true").param("resetToken", "tokenForjado")
                        .param("rol.nombre", "Administrador")
                        .param("roles[0].idRol", administrador.getIdRol().toString()))
                .andExpect(flash().attributeExists("exito"));
        assertThat(lector.getEstado()).isFalse();
        assertThat(lector.getResetToken()).isNull();
        assertThat(lector.getRol()).isEqualTo(auditor);
        assertThat(lector.getRoles()).containsExactly(auditor);
    }

    @Test
    void laInterfazOcultaAdministradorYMarcaCuentasProtegidasParaElOperador() throws Exception {
        mvc.perform(get("/usuarios").session(sesion(operador)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("esAdministrador", false))
                .andExpect(model().attribute("rolesAsignables", containsInAnyOrder(auditor, coordinador, medico)))
                .andExpect(model().attribute("idsUsuariosProtegidos", Set.of(admin.getIdUsuario())))
                .andExpect(content().string(containsString("Cuenta protegida")));
    }

    @Test
    void rechazaRolesInexistentesODeOtraAreaSinCambiarAlUsuario() throws Exception {
        mvc.perform(formulario(lector, auditor).session(sesion(admin))
                        .param("idsRoles", "999999"))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(formulario(lector, medico).session(sesion(admin)).param("area", "Administración"))
                .andExpect(flash().attributeExists("error"));
        assertThat(lector.getRol()).isEqualTo(auditor);
        assertThat(lector.getNombres()).isEqualTo("Nombre");
    }

    @Test
    void noPermiteRenombrarElRolReservadoAdministrador() throws Exception {
        mvc.perform(post("/roles/guardar").session(sesion(admin)).with(csrf())
                        .param("idRol", administrador.getIdRol().toString())
                        .param("nombre", "Otro nombre").param("area", "Administración"))
                .andExpect(flash().attributeExists("error"));
        assertThat(administrador.getNombre()).isEqualTo("Administrador");
    }

    @Test
    void administradorPuedeCambiarPermisosYLaRevocacionEsInmediata() throws Exception {
        MockHttpSession sesionAnterior = sesion(operador);
        mvc.perform(post("/roles/" + coordinador.getIdRol() + "/permisos")
                        .session(sesion(admin)).with(csrf()))
                .andExpect(flash().attributeExists("exito"));
        assertThat(coordinador.getPermisos()).isEmpty();
        mvc.perform(get("/usuarios").session(sesionAnterior)).andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void administradorAsignaPermisosAUnRolAdicionalYSeAplicanAlUsuario() throws Exception {
        Rol adicional = rol("Operador adicional", "Administración");
        lector.getRoles().add(adicional);
        usuarios.saveAndFlush(lector);
        mvc.perform(post("/roles/" + adicional.getIdRol() + "/permisos")
                        .session(sesion(admin)).with(csrf())
                        .param("permisoIds", crearUsuarios.getIdPermiso().toString(), editarUsuarios.getIdPermiso().toString()))
                .andExpect(flash().attributeExists("exito"));
        assertThat(autorizacion.tienePermiso(lector, "usuarios", "CREAR")).isTrue();
        assertThat(autorizacion.tienePermiso(lector, "usuarios", "EDITAR")).isTrue();
        assertThat(lector.getRol()).isEqualTo(auditor);
    }

    @Test
    void formularioDelRolAdministradorTieneNombreReservadoYTokenCsrf() throws Exception {
        mvc.perform(get("/roles/" + administrador.getIdRol() + "/editar").session(sesion(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("readonly=\"readonly\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void noAceptaPermisosInexistentesNiModificaLaAsignacionActual() throws Exception {
        mvc.perform(post("/roles/" + coordinador.getIdRol() + "/permisos")
                        .session(sesion(admin)).with(csrf())
                        .param("permisoIds", verUsuarios.getIdPermiso().toString(), "999999"))
                .andExpect(flash().attributeExists("error"));
        assertThat(coordinador.getPermisos()).containsExactlyInAnyOrder(verUsuarios, crearUsuarios, editarUsuarios, verRoles);
    }

    @Test
    void crearUnRolNoPermiteInyectarPermisosPorBinding() throws Exception {
        mvc.perform(post("/roles/guardar").session(sesion(admin)).with(csrf())
                        .param("nombre", "Rol nuevo").param("area", "Administración")
                        .param("permisos[0].idPermiso", editarRoles.getIdPermiso().toString()))
                .andExpect(flash().attributeExists("exito"));
        assertThat(roles.findByNombreIgnoreCase("Rol nuevo").orElseThrow().getPermisos()).isEmpty();
    }

    @Test
    void losServiciosTambienRechazanOperacionesNoAutorizadas() {
        Rol entrada = new Rol();
        entrada.setNombre("Rol indebido");
        entrada.setArea("Administración");
        assertThatThrownBy(() -> rolService.guardar(entrada, operador)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> rolService.asignarPermisos(coordinador.getIdRol(), List.of(), operador))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> usuarioService.cambiarEstado(admin.getIdUsuario(), false, operador))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unaCuentaDesactivadaPierdeLaSesionYElAcceso() throws Exception {
        MockHttpSession sesionAnterior = sesion(operador);
        operador.setEstado(false);
        usuarios.saveAndFlush(operador);
        mvc.perform(get("/usuarios").session(sesionAnterior))
                .andExpect(redirectedUrl("/login?cuentaInactiva"));
        assertThat(sesionAnterior.isInvalid()).isTrue();
    }

    private MockHttpServletRequestBuilder formulario(Usuario existente, Rol principal) {
        String nombreUsuario = existente == null ? "nuevo" : existente.getUsuario();
        MockHttpServletRequestBuilder request = post("/usuarios/guardar").with(csrf())
                .param("nombres", "Editado").param("apellidos", "Prueba")
                .param("correo", nombreUsuario + "@pruebas.local").param("usuario", nombreUsuario)
                .param("contrasena", existente == null ? "claveNueva" : "")
                .param("area", principal.getArea()).param("rol.idRol", principal.getIdRol().toString());
        if (existente != null) {
            request.param("idUsuario", existente.getIdUsuario().toString());
        }
        return request;
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
        usuario.setContrasena(hashInicial);
        usuario.setArea(principal.getArea());
        usuario.setRol(principal);
        usuario.setRoles(new LinkedHashSet<>(Set.of(principal)));
        return usuarios.save(usuario);
    }
}
