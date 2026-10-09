package com.evaluacion01.tecsup.service;

import com.evaluacion01.tecsup.entity.AuditoriaLog;
import com.evaluacion01.tecsup.entity.Rol;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.AuditoriaLogRepository;
import com.evaluacion01.tecsup.repository.RolRepository;
import com.evaluacion01.tecsup.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceImplTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Mock
    private RolRepository rolRepository;

    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private UsuarioServiceImpl usuarioService;

    private Rol rol(Integer id, String nombre) {
        Rol rol = new Rol();
        rol.setIdRol(id);
        rol.setNombre(nombre);
        rol.setArea("Administración");
        return rol;
    }

    private Usuario existente() {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(7L);
        usuario.setNombres("Ana");
        usuario.setApellidos("Torres");
        usuario.setDni("12345678");
        usuario.setCorreo("ana@hospital.pe");
        usuario.setTelefono("999999999");
        usuario.setUsuario("atorres");
        usuario.setContrasena("$2a$hash");
        usuario.setArea("Administración");
        usuario.setEstado(true);
        usuario.setRol(rol(1, "Administrador"));
        return usuario;
    }

    @Test
    void registrarUsuarioDebeCrearElRegistroDeAuditoria() {
        Usuario nuevo = existente();
        nuevo.setIdUsuario(null);
        nuevo.setRol(rol(2, "Auditor administrativo"));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> {
            Usuario guardado = invocation.getArgument(0);
            guardado.setIdUsuario(11L);
            return guardado;
        });

        Usuario guardado = usuarioService.registrarUsuario(nuevo);

        verify(auditoriaService).registrar(eq(AuditoriaService.MODULO_USUARIOS), eq("CREAR_USUARIO"),
                eq("Usuario"), eq(11L), contains("con rol Auditor administrativo"));
        assertThat(guardado.getIdUsuario()).isEqualTo(11L);
    }

    @Test
    void actualizarUsuarioDebeDetallarLosCamposModificados() {
        Usuario existente = existente();
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(existente));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(rolRepository.findById(1)).thenReturn(Optional.of(rol(1, "Administrador")));

        Usuario cambios = existente();
        cambios.setCorreo("ana.actualizada@hospital.pe");
        cambios.setTelefono("888888888");
        cambios.setRol(rol(1, null));

        usuarioService.actualizarUsuario(7L, cambios);

        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(auditoriaService).registrar(eq(AuditoriaService.MODULO_USUARIOS), eq("EDITAR_USUARIO"),
                eq("Usuario"), eq(7L), detalle.capture());
        assertThat(detalle.getValue())
                .contains("correo: 'ana@hospital.pe' -> 'ana.actualizada@hospital.pe'")
                .contains("telefono: '999999999' -> '888888888'");
    }

    @Test
    void cambiarEstadoDebeRegistrarElCambioDeCuenta() {
        Usuario existente = existente();
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(existente));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        usuarioService.cambiarEstado(7L);

        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(auditoriaService).registrar(eq(AuditoriaService.MODULO_USUARIOS), eq("CAMBIAR_ESTADO_USUARIO"),
                eq("Usuario"), eq(7L), detalle.capture());
        assertThat(detalle.getValue()).isEqualTo("Estado cambiado de activo a inactivo");
        assertThat(existente.getEstado()).isFalse();
    }

    @Test
    void actualizarPasswordDebeDejarConstanciaSinExponerLaClave() {
        Usuario existente = existente();
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(existente));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode("nuevaClave")).thenReturn("$2a$nueva");

        Usuario cambios = existente();
        cambios.setContrasena("nuevaClave");

        usuarioService.actualizarUsuario(7L, cambios);

        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(auditoriaService).registrar(eq(AuditoriaService.MODULO_USUARIOS), eq("EDITAR_USUARIO"),
                eq("Usuario"), eq(7L), detalle.capture());
        assertThat(detalle.getValue()).contains("contrasena: '****' -> '****'");
        assertThat(detalle.getValue()).doesNotContain("nuevaClave");
    }

    @Test
    void ningunaOperacionDeUsuarioDebeRomperseSiLaAuditoriaFalla() {
        // Se usa el servicio real de auditoría con un repositorio caído para comprobar
        // que un fallo al registrar el log no interrumpe la operación crítica.
        AuditoriaLogRepository repositorioCaido = org.mockito.Mockito.mock(AuditoriaLogRepository.class);
        when(repositorioCaido.save(any(AuditoriaLog.class))).thenThrow(new RuntimeException("BD caída"));

        UsuarioServiceImpl servicio = new UsuarioServiceImpl(usuarioRepository, passwordEncoder, rolRepository,
                new AuditoriaService(repositorioCaido));

        Usuario existente = existente();
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(existente));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        org.assertj.core.api.Assertions.assertThatCode(() -> servicio.cambiarEstado(7L))
                .doesNotThrowAnyException();

        assertThat(existente.getEstado()).isFalse();
    }
}
