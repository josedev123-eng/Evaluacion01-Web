package com.evaluacion01.tecsup;

import com.evaluacion01.tecsup.entity.Permiso;
import com.evaluacion01.tecsup.entity.Rol;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.AuditoriaLogRepository;
import com.evaluacion01.tecsup.repository.PermisoRepository;
import com.evaluacion01.tecsup.repository.RolRepository;
import com.evaluacion01.tecsup.repository.UsuarioRepository;
import com.evaluacion01.tecsup.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MultirrolIntegracionTests {

    @Autowired private UsuarioService service;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private RolRepository roles;
    @Autowired private PermisoRepository permisos;
    @Autowired private AuditoriaLogRepository logs;
    private Rol principal;
    private Rol adicional;
    private Rol administrador;
    private Usuario admin;
    private Usuario destino;
    private Usuario editor;

    @BeforeEach
    void preparar() {
        logs.deleteAll();
        principal = rol("Principal integración");
        adicional = rol("Adicional integración");
        administrador = rol("Administrador");
        Rol rolEditor = rol("Editor integración");
        Permiso permiso = new Permiso();
        permiso.setModulo("usuarios");
        permiso.setNombre("EDITAR");
        rolEditor.getPermisos().add(permisos.saveAndFlush(permiso));
        admin = usuario("adminIntegracion", administrador);
        destino = usuario("destinoIntegracion", principal);
        editor = usuario("editorIntegracion", rolEditor);
    }

    @Test
    void asignarYRetirarConservaPrincipalCatalogoEIdentidadDelEjecutor() {
        service.asignarRolAdicional(destino.getIdUsuario(), adicional.getIdRol().longValue(), admin);
        assertThat(destino.getRolesAsignados()).extracting(Rol::getIdRol)
                .contains(principal.getIdRol(), adicional.getIdRol());
        service.removerRolAdicional(destino.getIdUsuario(), adicional.getIdRol().longValue(), admin);
        assertThat(destino.getRolesAsignados()).extracting(Rol::getIdRol).containsExactly(principal.getIdRol());
        assertThat(roles.existsById(adicional.getIdRol())).isTrue();
        assertThat(logs.findAll()).hasSize(2).allSatisfy(evento ->
                assertThat(evento.getIdUsuarioEjecutor()).isEqualTo(admin.getIdUsuario()));
    }

    @Test
    @SuppressWarnings("deprecation")
    void laFirmaAnteriorSinOperadorYaNoPermiteModificarRoles() {
        assertThatThrownBy(() -> service.asignarRolAdicional(destino.getIdUsuario(), adicional.getIdRol().longValue()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(destino.getRolesAsignados()).hasSize(1);
    }

    @Test
    void unEditorNoPuedeConcederAdministrador() {
        assertThatThrownBy(() -> service.asignarRolAdicional(destino.getIdUsuario(), administrador.getIdRol().longValue(), editor))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(destino.getRolesAsignados()).hasSize(1);
    }

    @Test
    void unEditorNoPuedeCambiarSusPropiosRoles() {
        assertThatThrownBy(() -> service.asignarRolAdicional(editor.getIdUsuario(), adicional.getIdRol().longValue(), editor))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void noPermiteRetirarElAdministradorAdicionalDeLaUltimaCuentaActiva() {
        admin.setEstado(false);
        destino.getRoles().add(administrador);
        usuarios.flush();
        assertThatThrownBy(() -> service.removerRolAdicional(destino.getIdUsuario(), administrador.getIdRol().longValue(), destino))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("último administrador");
        assertThat(destino.getRolesAsignados()).extracting(Rol::getIdRol).contains(administrador.getIdRol());
    }

    @Test
    void noPermiteRetirarElRolPrincipal() {
        assertThatThrownBy(() -> service.removerRolAdicional(destino.getIdUsuario(), principal.getIdRol().longValue(), admin))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("principal");
    }

    @Test
    void noPermiteAsignarUnRolInactivo() {
        adicional.setEstado(false);
        assertThatThrownBy(() -> service.asignarRolAdicional(destino.getIdUsuario(), adicional.getIdRol().longValue(), admin))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("inactivo");
    }

    @Test
    void unIdLongNoSeTruncaParaElegirOtroRol() {
        long idManipulado = (1L << 32) + adicional.getIdRol();
        assertThatThrownBy(() -> service.asignarRolAdicional(destino.getIdUsuario(), idManipulado, admin))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("identificador");
    }

    private Rol rol(String nombre) {
        Rol rol = new Rol();
        rol.setNombre(nombre);
        rol.setArea("Medicina");
        return roles.saveAndFlush(rol);
    }

    private Usuario usuario(String nombre, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setUsuario(nombre);
        usuario.setCorreo(nombre + "@pruebas.local");
        usuario.setNombres("Prueba");
        usuario.setApellidos("Integración");
        usuario.setContrasena("hashSoloParaFixture");
        usuario.setArea("Medicina");
        usuario.setRol(rol);
        usuario.setRoles(new LinkedHashSet<>(Set.of(rol)));
        return usuarios.saveAndFlush(usuario);
    }
}
