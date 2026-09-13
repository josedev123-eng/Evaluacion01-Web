package com.evaluacion01.tecsup.service;

import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.UsuarioRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Autowired
    public UsuarioServiceImpl(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    @Override
    public Usuario obtenerPorId(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + id));
    }

    @Override
    public Usuario registrarUsuario(Usuario usuario) {
        return usuarioRepository.save(usuario);
    }

    @Override
    public Usuario actualizarUsuario(Long id, Usuario usuarioActualizado) {
        Usuario usuarioExistente = obtenerPorId(id);

        usuarioExistente.setNombres(usuarioActualizado.getNombres());
        usuarioExistente.setApellidos(usuarioActualizado.getApellidos());
        usuarioExistente.setDni(usuarioActualizado.getDni());
        usuarioExistente.setCorreo(usuarioActualizado.getCorreo());
        usuarioExistente.setTelefono(usuarioActualizado.getTelefono());
        usuarioExistente.setUsuario(usuarioActualizado.getUsuario());

        if (usuarioActualizado.getContrasena() != null && !usuarioActualizado.getContrasena().isEmpty()) {
            usuarioExistente.setContrasena(usuarioActualizado.getContrasena());
        }

        usuarioExistente.setArea(usuarioActualizado.getArea());
        usuarioExistente.setEstado(usuarioActualizado.getEstado());

        if (usuarioActualizado.getRol() != null) {
            usuarioExistente.setRol(usuarioActualizado.getRol());
        }

        return usuarioRepository.save(usuarioExistente);
    }
}
