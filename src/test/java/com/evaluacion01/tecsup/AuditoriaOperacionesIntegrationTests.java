package com.evaluacion01.tecsup;

import com.evaluacion01.tecsup.audit.ModuloAuditoria;
import com.evaluacion01.tecsup.audit.ResultadoAuditoria;
import com.evaluacion01.tecsup.entity.*;
import com.evaluacion01.tecsup.repository.*;
import com.evaluacion01.tecsup.service.RolService;
import com.evaluacion01.tecsup.service.UsuarioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditoriaOperacionesIntegrationTests {

    @Autowired private MockMvc mvc;
    @Autowired private AuditoriaLogRepository logs;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private RolRepository roles;
    @Autowired private PermisoRepository permisos;
    @Autowired private UsuarioService usuarioService;
    @Autowired private RolService rolService;
    @Autowired private PasswordEncoder encoder;
    @Autowired private PlatformTransactionManager transactionManager;

    private Rol administrador;
    private Rol coordinador;
    private Rol lector;
    private Permiso editar;
    private Usuario admin;
    private Usuario operador;
    private Usuario consulta;
    private Usuario destino;

    @BeforeEach
    void preparar() {
        logs.deleteAll();
        Permiso ver = permiso("VER");
        Permiso crear = permiso("CREAR");
        editar = permiso("EDITAR");
        administrador = rol("Administrador");
        coordinador = rol("Coordinador de auditoría", ver, crear, editar);
        lector = rol("Lector de auditoría", ver);
        admin = usuario("adminAudit", administrador);
        operador = usuario("operadorAudit", coordinador);
        consulta = usuario("consultaAudit", lector);
        destino = usuario("destinoAudit", lector);
    }

    @AfterEach
    void limpiarDatosH2() {
        usuarios.deleteAll();
        roles.deleteAll();
        permisos.deleteAll();
        logs.deleteAll();
    }

    @Test
    void crearUsuarioRegistraActorEntidadYRolesSinDatosPersonalesNiSecretos() throws Exception {
        mvc.perform(formulario(null, coordinador).session(sesion(admin)))
                .andExpect(flash().attributeExists("exito"));

        Usuario nuevo = usuarios.findByUsuario("nuevoAudit").orElseThrow();
        AuditoriaLog evento = unico("CREAR_USUARIO", ResultadoAuditoria.EXITO);
        assertThat(evento.getIdUsuarioEjecutor()).isEqualTo(admin.getIdUsuario());
        assertThat(evento.getIdEntidad()).isEqualTo(nuevo.getIdUsuario());
        assertThat(evento.getDetalle()).contains(coordinador.getIdRol().toString())
                .doesNotContain("claveSuperSecreta", nuevo.getContrasena(), "nuevoAudit@pruebas.local", "Nombre privado");
        assertThat(evento.getRuta()).isEqualTo("/usuarios/guardar");
    }

    @Test
    void editarUsuarioInformaLosCamposYLosRolesAnterioresYPosteriores() throws Exception {
        mvc.perform(formulario(destino, coordinador).session(sesion(admin))
                        .param("idsRoles", lector.getIdRol().toString()))
                .andExpect(flash().attributeExists("exito"));

        AuditoriaLog evento = unico("EDITAR_USUARIO", ResultadoAuditoria.EXITO);
        assertThat(evento.getIdEntidad()).isEqualTo(destino.getIdUsuario());
        assertThat(evento.getDetalle()).contains("nombres", "rol_principal", "roles", lector.getIdRol().toString(),
                coordinador.getIdRol().toString()).doesNotContain("Nombre privado");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void registraActivacionYDesactivacionExplicitas(boolean activo) throws Exception {
        destino.setEstado(!activo);
        usuarios.saveAndFlush(destino);

        mvc.perform(post("/usuarios/" + destino.getIdUsuario() + "/estado").with(csrf())
                        .session(sesion(admin)).param("activo", Boolean.toString(activo)))
                .andExpect(flash().attributeExists("exito"));

        AuditoriaLog evento = unico(activo ? "ACTIVAR_USUARIO" : "DESACTIVAR_USUARIO", ResultadoAuditoria.EXITO);
        assertThat(evento.getDetalle()).isEqualTo("Estado: " + !activo + " -> " + activo);
        assertThat(usuarios.findById(destino.getIdUsuario()).orElseThrow().getEstado()).isEqualTo(activo);
    }

    @Test
    void crearYEditarRolGeneraUnEventoPorOperacion() throws Exception {
        mvc.perform(post("/roles/guardar").with(csrf()).session(sesion(admin))
                        .param("nombre", "Rol auditado").param("area", "Administración"))
                .andExpect(flash().attributeExists("exito"));
        Rol nuevo = roles.findByNombreIgnoreCase("Rol auditado").orElseThrow();
        unico("CREAR_ROL", ResultadoAuditoria.EXITO);

        mvc.perform(post("/roles/guardar").with(csrf()).session(sesion(admin))
                        .param("idRol", nuevo.getIdRol().toString()).param("nombre", "Rol modificado")
                        .param("area", "Administración").param("descripcion", "Descripción privada"))
                .andExpect(flash().attributeExists("exito"));

        assertThat(logs.findAll()).hasSize(2);
        assertThat(logs.findAll()).filteredOn(evento -> "EDITAR_ROL".equals(evento.getAccion()))
                .singleElement().satisfies(evento -> {
                    assertThat(evento.getModulo()).isEqualTo(ModuloAuditoria.ROLES);
                    assertThat(evento.getIdEntidad()).isEqualTo(nuevo.getIdRol().longValue());
                    assertThat(evento.getDetalle()).contains("nombre", "descripcion").doesNotContain("Descripción privada");
                });
    }

    @Test
    void asignarYRevocarPermisosConservaIdsAnterioresYPosteriores() throws Exception {
        mvc.perform(post("/roles/" + lector.getIdRol() + "/permisos").with(csrf()).session(sesion(admin))
                        .param("permisoIds", editar.getIdPermiso().toString()))
                .andExpect(flash().attributeExists("exito"));
        AuditoriaLog primero = unico("ASIGNAR_PERMISOS", ResultadoAuditoria.EXITO);
        assertThat(primero.getModulo()).isEqualTo(ModuloAuditoria.PERMISOS);
        assertThat(primero.getDetalle()).contains("-> [" + editar.getIdPermiso() + "]");

        mvc.perform(post("/roles/" + lector.getIdRol() + "/permisos").with(csrf()).session(sesion(admin)))
                .andExpect(flash().attributeExists("exito"));
        assertThat(logs.findAll()).hasSize(2);
        assertThat(logs.findAll()).anySatisfy(evento ->
                assertThat(evento.getDetalle()).isEqualTo("Permisos: [" + editar.getIdPermiso() + "] -> []"));
    }

    @Test
    void unRechazoDelControladorQuedaRegistradoSinModificarLaCuenta() throws Exception {
        mvc.perform(formulario(destino, lector).session(sesion(consulta)))
                .andExpect(flash().attributeExists("error"));

        AuditoriaLog evento = unico("EDITAR_USUARIO", ResultadoAuditoria.DENEGADO);
        assertThat(evento.getIdUsuarioEjecutor()).isEqualTo(consulta.getIdUsuario());
        assertThat(usuarios.findById(destino.getIdUsuario()).orElseThrow().getNombres()).isEqualTo("Original");
    }

    @Test
    void unOperadorNoPuedeModificarAlAdministradorYElIntentoSeConserva() throws Exception {
        mvc.perform(formulario(admin, administrador).session(sesion(operador)))
                .andExpect(flash().attributeExists("error"));

        AuditoriaLog evento = unico("EDITAR_USUARIO", ResultadoAuditoria.DENEGADO);
        assertThat(evento.getIdUsuarioEjecutor()).isEqualTo(operador.getIdUsuario());
        assertThat(evento.getIdEntidad()).isEqualTo(admin.getIdUsuario());
        assertThat(usuarios.findById(admin.getIdUsuario()).orElseThrow().getNombres()).isEqualTo("Original");
    }

    @Test
    void losServiciosAuditanLosRechazosSinDependerDelControlador() {
        assertThatThrownBy(() -> rolService.asignarPermisos(lector.getIdRol(), List.of(editar.getIdPermiso()), operador))
                .isInstanceOf(AccessDeniedException.class);

        AuditoriaLog evento = unico("ASIGNAR_PERMISOS", ResultadoAuditoria.DENEGADO);
        assertThat(evento.getRuta()).isNull();
        assertThat(evento.getIdUsuarioEjecutor()).isEqualTo(operador.getIdUsuario());
    }

    @Test
    void unNombreDeUsuarioDuplicadoNoProduceUnExitoFalso() throws Exception {
        mvc.perform(formulario(null, lector).session(sesion(admin))
                        .with(request -> { request.setParameter("usuario", destino.getUsuario()); return request; }))
                .andExpect(flash().attributeExists("error"));

        unico("CREAR_USUARIO", ResultadoAuditoria.FALLO);
        assertThat(usuarios.findByUsuario("nuevoAudit")).isEmpty();
    }

    @Test
    void unRolInexistenteQuedaComoOperacionRechazada() throws Exception {
        mvc.perform(formulario(null, lector).session(sesion(admin))
                        .with(request -> { request.setParameter("rol.idRol", "999999"); return request; }))
                .andExpect(flash().attributeExists("error"));

        unico("CREAR_USUARIO", ResultadoAuditoria.FALLO);
        assertThat(usuarios.findByUsuario("nuevoAudit")).isEmpty();
    }

    @Test
    void datosIncompletosSeAuditanAntesDeLlegarAlServicio() throws Exception {
        mvc.perform(post("/usuarios/guardar").with(csrf()).session(sesion(admin)).param("nombres", "Incompleto"))
                .andExpect(flash().attributeExists("error"));

        AuditoriaLog evento = unico("CREAR_USUARIO", ResultadoAuditoria.FALLO);
        assertThat(evento.getDetalle()).isEqualTo("FORMULARIO_INVALIDO");
    }

    @Test
    void csrfRechazadoGeneraUnEventoDenegadoSinEjecutarLaOperacion() throws Exception {
        mvc.perform(post("/roles/guardar").session(sesion(admin))
                        .param("nombre", "Rol forjado").param("area", "Administración"))
                .andExpect(status().isForbidden());

        unico("CSRF_RECHAZADO", ResultadoAuditoria.DENEGADO);
        assertThat(roles.findByNombreIgnoreCase("Rol forjado")).isEmpty();
    }

    @Test
    void operarSinSesionSeRegistraComoAccesoDenegado() throws Exception {
        mvc.perform(formulario(null, lector)).andExpect(redirectedUrl("/login"));

        AuditoriaLog evento = unico("ACCESO_DENEGADO", ResultadoAuditoria.DENEGADO);
        assertThat(evento.getUsuarioEjecutor()).isEqualTo("ANONIMO");
        assertThat(evento.getDetalle()).isEqualTo("SIN_SESION");
        assertThat(usuarios.findByUsuario("nuevoAudit")).isEmpty();
    }

    @Test
    void revertirLaTransaccionRevierteTantoElUsuarioComoSuEventoExitoso() {
        Usuario entrada = new Usuario();
        entrada.setNombres("Prueba");
        entrada.setApellidos("Rollback");
        entrada.setUsuario("rollbackAudit");
        entrada.setCorreo("rollbackAudit@pruebas.local");
        entrada.setContrasena("claveValida");
        entrada.setArea("Administración");
        entrada.setRol(lector);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            usuarioService.registrarUsuario(entrada, List.of(), admin);
            status.setRollbackOnly();
        });

        assertThat(usuarios.findByUsuario("rollbackAudit")).isEmpty();
        assertThat(logs.count()).isZero();
    }

    private AuditoriaLog unico(String accion, ResultadoAuditoria resultado) {
        assertThat(logs.findAll()).hasSize(1);
        AuditoriaLog evento = logs.findAll().get(0);
        assertThat(evento.getAccion()).isEqualTo(accion);
        assertThat(evento.getResultado()).isEqualTo(resultado);
        return evento;
    }

    private MockHttpSession sesion(Usuario usuario) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogueado", usuario);
        return session;
    }

    private MockHttpServletRequestBuilder formulario(Usuario existente, Rol principal) {
        String nombre = existente == null ? "nuevoAudit" : existente.getUsuario();
        MockHttpServletRequestBuilder request = post("/usuarios/guardar").with(csrf())
                .param("nombres", "Nombre privado").param("apellidos", "Prueba")
                .param("correo", nombre + "@pruebas.local").param("usuario", nombre)
                .param("contrasena", existente == null ? "claveSuperSecreta" : "")
                .param("area", "Administración").param("rol.idRol", principal.getIdRol().toString());
        if (existente != null) request.param("idUsuario", existente.getIdUsuario().toString());
        return request;
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
        rol.setArea("Administración");
        rol.setPermisos(new LinkedHashSet<>(List.of(asignados)));
        return roles.saveAndFlush(rol);
    }

    private Usuario usuario(String nombre, Rol principal) {
        Usuario usuario = new Usuario();
        usuario.setNombres("Original");
        usuario.setApellidos("Prueba");
        usuario.setUsuario(nombre);
        usuario.setCorreo(nombre + "@pruebas.local");
        usuario.setContrasena(encoder.encode("claveValida"));
        usuario.setRol(principal);
        usuario.setRoles(new LinkedHashSet<>(Set.of(principal)));
        usuario.setArea("Administración");
        return usuarios.saveAndFlush(usuario);
    }
}
