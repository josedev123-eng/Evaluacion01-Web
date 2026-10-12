package com.evaluacion01.tecsup.service;

import com.evaluacion01.tecsup.entity.Permiso;
import com.evaluacion01.tecsup.entity.Rol;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutorizacionServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private AutorizacionService autorizacionService;

    @Test
    void combinaLosPermisosDeTodosLosRolesSinConfundirModulos() {
        Rol principal = rol(1, "Lector", permiso("usuarios", "VER"));
        Rol adicional = rol(2, "Editor de roles", permiso("roles", "VER"));
        Usuario usuario = usuario(principal, adicional);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        assertThat(autorizacionService.tienePermiso(usuario, "USUARIOS", "ver")).isTrue();
        assertThat(autorizacionService.tienePermiso(usuario, "roles", "VER")).isTrue();
        assertThat(autorizacionService.tienePermiso(usuario, "usuarios", "EDITAR")).isFalse();
        assertThat(autorizacionService.tienePermiso(usuario, "otro", "VER")).isFalse();
    }

    @Test
    void reconoceAdministradorComoRolAdicional() {
        Usuario usuario = usuario(rol(1, "Coordinador"), rol(2, " administrador "));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        assertThat(autorizacionService.esAdministrador(usuario)).isTrue();
        assertThat(autorizacionService.tienePermiso(usuario, "roles", "EDITAR")).isTrue();
    }

    @Test
    void mantieneElRolPrincipalDeUsuariosAnterioresSinFilasAdicionales() {
        Usuario usuario = usuario(rol(1, "Lector", permiso("usuarios", "VER")));
        usuario.setRoles(new LinkedHashSet<>());
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        assertThat(autorizacionService.tienePermiso(usuario, "usuarios", "VER")).isTrue();
    }

    @Test
    void noConfiaEnUnAdministradorObsoletoOFalsificadoEnLaSesion() {
        Usuario enSesion = usuario(rol(1, "Administrador"));
        Usuario enBase = usuario(rol(2, "Lector", permiso("usuarios", "VER")));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(enBase));

        assertThat(autorizacionService.esAdministrador(enSesion)).isFalse();
        assertThat(autorizacionService.tienePermiso(enSesion, "usuarios", "EDITAR")).isFalse();
    }

    @Test
    void unaCuentaInactivaNoTienePermisosAunqueSeaAdministradora() {
        Usuario usuario = usuario(rol(1, "Administrador"));
        usuario.setEstado(false);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        assertThat(autorizacionService.tienePermiso(usuario, "usuarios", "VER")).isFalse();
        assertThat(autorizacionService.esAdministrador(usuario)).isFalse();
    }

    @Test
    void unaCuentaEliminadaPierdeElAcceso() {
        Usuario usuario = usuario(rol(1, "Administrador"));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(autorizacionService.tienePermiso(usuario, "usuarios", "VER")).isFalse();
    }

    @Test
    void rechazaIdentidadesSinIdYSinSesion() {
        assertThat(autorizacionService.tienePermiso(null, "usuarios", "VER")).isFalse();
        assertThat(autorizacionService.esAdministrador(new Usuario())).isFalse();
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    void unPermisoDeEditarRolesNoConcedeAdministracionANoAdministradores() {
        Usuario usuario = usuario(rol(1, "Operador", permiso("roles", "EDITAR"), permiso("usuarios", "EDITAR")));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        assertThat(autorizacionService.tienePermiso(usuario, "roles", "EDITAR")).isFalse();
        assertThat(autorizacionService.tienePermiso(usuario, "usuarios", "EDITAR")).isTrue();
    }

    @Test
    void unRolInactivoNoOtorgaSusPermisosPeroLosDemasRolesSi() {
        Rol principal = rol(1, "Coordinador", permiso("usuarios", "VER"), permiso("usuarios", "EDITAR"));
        Rol adicional = rol(2, "Auditor", permiso("roles", "VER"));
        principal.setEstado(false);
        Usuario usuario = usuario(principal, adicional);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        assertThat(autorizacionService.tienePermiso(usuario, "usuarios", "VER")).isFalse();
        assertThat(autorizacionService.tienePermiso(usuario, "usuarios", "EDITAR")).isFalse();
        assertThat(autorizacionService.tienePermiso(usuario, "roles", "VER")).isTrue();

        principal.setEstado(true);
        assertThat(autorizacionService.tienePermiso(usuario, "usuarios", "EDITAR")).isTrue();
    }

    private Usuario usuario(Rol principal, Rol... adicionales) {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(1L);
        usuario.setRol(principal);
        usuario.setRoles(new LinkedHashSet<>(Set.of(adicionales)));
        return usuario;
    }

    private Rol rol(int id, String nombre, Permiso... permisos) {
        Rol rol = new Rol();
        rol.setIdRol(id);
        rol.setNombre(nombre);
        rol.setPermisos(new LinkedHashSet<>(Set.of(permisos)));
        return rol;
    }

    private Permiso permiso(String modulo, String nombre) {
        Permiso permiso = new Permiso();
        permiso.setModulo(modulo);
        permiso.setNombre(nombre);
        return permiso;
    }
}
