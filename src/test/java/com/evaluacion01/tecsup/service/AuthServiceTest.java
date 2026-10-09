package com.evaluacion01.tecsup.service;

import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private AuthService authService;

    private Usuario usuarioActivo() {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(5L);
        usuario.setUsuario("jdoe");
        usuario.setCorreo("jdoe@hospital.pe");
        usuario.setContrasena("$2a$hash");
        usuario.setEstado(true);
        return usuario;
    }

    @Test
    void loginExitosoDebeRegistrarElEventoDeAuditoria() throws Exception {
        Usuario usuario = usuarioActivo();
        when(usuarioRepository.findByUsuarioOrCorreo("jdoe", "jdoe")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("clave123", "$2a$hash")).thenReturn(true);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Usuario resultado = authService.autenticar("jdoe", "clave123");

        assertThat(resultado.getUsuario()).isEqualTo("jdoe");
        verify(auditoriaService).registrar(eq("jdoe"), eq(AuditoriaService.MODULO_AUTENTICACION),
                eq("LOGIN_EXITOSO"), eq("Usuario"), eq(5L), anyString(), eq(AuditoriaService.RESULTADO_EXITO));
    }

    @Test
    void claveIncorrectaDebeRegistrarIntentoFallidoYRespertarElMensaje() {
        Usuario usuario = usuarioActivo();
        when(usuarioRepository.findByUsuarioOrCorreo("jdoe", "jdoe")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("mala", "$2a$hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.autenticar("jdoe", "mala"))
                .isInstanceOf(Exception.class)
                .hasMessage("La contraseña es incorrecta.");

        verify(auditoriaService).registrarFallo(eq("jdoe"), eq(AuditoriaService.MODULO_AUTENTICACION),
                eq("LOGIN_FALLIDO"), eq("Usuario"), any(), eq("Contraseña incorrecta"));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void usuarioInexistenteDebeRegistrarIntentoFallido() {
        when(usuarioRepository.findByUsuarioOrCorreo("desconocido", "desconocido")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.autenticar("desconocido", "clave123"))
                .isInstanceOf(Exception.class)
                .hasMessage("El usuario o correo ingresado no existe.");

        verify(auditoriaService).registrarFallo(eq("desconocido"), eq(AuditoriaService.MODULO_AUTENTICACION),
                eq("LOGIN_FALLIDO"), eq("Usuario"), any(), eq("Usuario o correo inexistente"));
    }

    @Test
    void cuentaInactivaDebeRegistrarIntentoFallido() {
        Usuario usuario = usuarioActivo();
        usuario.setEstado(false);
        when(usuarioRepository.findByUsuarioOrCorreo("jdoe", "jdoe")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("clave123", "$2a$hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.autenticar("jdoe", "clave123"))
                .isInstanceOf(Exception.class)
                .hasMessage("Su cuenta se encuentra inactiva.");

        verify(auditoriaService).registrarFallo(eq("jdoe"), eq(AuditoriaService.MODULO_AUTENTICACION),
                eq("LOGIN_FALLIDO"), eq("Usuario"), any(), eq("Cuenta inactiva"));
    }

    @Test
    void credencialesVaciasNoDebenGenerarRegistroDeAuditoria() {
        assertThatThrownBy(() -> authService.autenticar("  ", "clave123"))
                .hasMessage("Debe ingresar un usuario o correo electrónico.");

        verify(auditoriaService, never()).registrarFallo(anyString(), anyString(), anyString(), anyString(),
                anyLong(), anyString());
        verify(auditoriaService, never()).registrar(anyString(), anyString(), anyString(), anyString(),
                anyLong(), anyString(), anyString());
    }
}
