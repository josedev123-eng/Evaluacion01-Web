package com.evaluacion01.tecsup.service;

import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    public Usuario autenticar(String identificador, String contrasena) throws Exception {
        if (identificador == null || identificador.trim().isEmpty()) {
            throw new Exception("Debe ingresar un usuario o correo electrónico.");
        }
        if (contrasena == null || contrasena.trim().isEmpty()) {
            throw new Exception("Debe ingresar su contraseña.");
        }

        Optional<Usuario> usuarioOpt = usuarioRepository.findByUsuarioOrCorreo(identificador.trim(), identificador.trim());

        if (usuarioOpt.isEmpty()) {
            throw new Exception("El usuario o correo ingresado no existe.");
        }

        Usuario usuario = usuarioOpt.get();

        if (!usuario.getContrasena().equals(contrasena)) {
            throw new Exception("La contraseña es incorrecta.");
        }

        if (Boolean.FALSE.equals(usuario.getEstado())) {
            throw new Exception("Su cuenta se encuentra inactiva.");
        }

        return usuario;
    }
}