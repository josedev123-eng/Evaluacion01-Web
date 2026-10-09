package com.evaluacion01.tecsup.service;

import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AuthService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuditoriaService auditoriaService;

    public Usuario autenticar(String identificador, String contrasena) throws Exception {
        if (identificador == null || identificador.trim().isEmpty()) {
            throw new Exception("Debe ingresar un usuario o correo electrónico.");
        }
        if (contrasena == null || contrasena.trim().isEmpty()) {
            throw new Exception("Debe ingresar su contraseña.");
        }

        Optional<Usuario> usuarioOpt = usuarioRepository.findByUsuarioOrCorreo(identificador.trim(), identificador.trim());

        if (usuarioOpt.isEmpty()) {
            registrarIntentoFallido(identificador, "Usuario o correo inexistente");
            throw new Exception("El usuario o correo ingresado no existe.");
        }

        Usuario usuario = usuarioOpt.get();

        if (!passwordEncoder.matches(contrasena, usuario.getContrasena())) {
            registrarIntentoFallido(usuario.getUsuario(), "Contraseña incorrecta");
            throw new Exception("La contraseña es incorrecta.");
        }

        if (Boolean.FALSE.equals(usuario.getEstado())) {
            registrarIntentoFallido(usuario.getUsuario(), "Cuenta inactiva");
            throw new Exception("Su cuenta se encuentra inactiva.");
        }

        usuario.setUltimoAcceso(LocalDateTime.now());
        usuarioRepository.save(usuario);

        auditoriaService.registrar(usuario.getUsuario(), AuditoriaService.MODULO_AUTENTICACION,
                "LOGIN_EXITOSO", "Usuario", usuario.getIdUsuario(),
                "Inicio de sesión de " + usuario.getUsuario(), AuditoriaService.RESULTADO_EXITO);

        return usuario;
    }

    // RF-AUD-01: deja constancia de los intentos de acceso rechazados.
    private void registrarIntentoFallido(String identificador, String motivo) {
        auditoriaService.registrarFallo(identificador, AuditoriaService.MODULO_AUTENTICACION,
                "LOGIN_FALLIDO", "Usuario", null, motivo);
    }
}
