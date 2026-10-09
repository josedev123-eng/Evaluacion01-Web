package com.evaluacion01.tecsup.service;

import com.evaluacion01.tecsup.audit.ModuloAuditoria;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(rollbackFor = Exception.class)
    public Usuario autenticar(String identificador, String contrasena) throws Exception {
        Usuario candidato = null;
        String motivo = "DATOS_INCOMPLETOS";
        boolean denegado = false;
        try {
            if (identificador == null || identificador.trim().isEmpty()) {
                throw new Exception("Debe ingresar un usuario o correo electrónico.");
            }
            if (contrasena == null || contrasena.trim().isEmpty()) {
                throw new Exception("Debe ingresar su contraseña.");
            }
            motivo = "CREDENCIALES_INVALIDAS";
            Optional<Usuario> usuarioOpt = usuarioRepository.findByUsuarioOrCorreo(identificador.trim(), identificador.trim());
            if (usuarioOpt.isEmpty()) {
                throw new Exception("El usuario o correo ingresado no existe.");
            }
            candidato = usuarioOpt.get();
            if (!passwordEncoder.matches(contrasena, candidato.getContrasena())) {
                throw new Exception("La contraseña es incorrecta.");
            }
            if (Boolean.FALSE.equals(candidato.getEstado())) {
                motivo = "CUENTA_INACTIVA";
                denegado = true;
                throw new Exception("Su cuenta se encuentra inactiva.");
            }
            motivo = "ERROR_AUTENTICACION";
            candidato.setUltimoAcceso(LocalDateTime.now());
            usuarioRepository.save(candidato);
            auditoriaService.registrarExito(candidato, ModuloAuditoria.AUTENTICACION, "LOGIN", "Usuario",
                    candidato.getIdUsuario(), "Inicio de sesión correcto");
            return candidato;
        } catch (Exception e) {
            auditoriaService.registrarFallo(candidato, identificador, ModuloAuditoria.AUTENTICACION, "LOGIN", "Usuario",
                    candidato == null ? null : candidato.getIdUsuario(), motivo, denegado);
            throw e;
        }
    }
}
