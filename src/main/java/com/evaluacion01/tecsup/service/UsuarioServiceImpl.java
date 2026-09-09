package com.evaluacion01.tecsup.service;

import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;

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

    // RF-USR-01: Registrar Usuario
    @Override
    public Usuario registrarUsuario(Usuario usuario) {
        if (usuarioRepository.existsByUsuario(usuario.getUsuario())) {
            throw new RuntimeException("El nombre de usuario ya está registrado");
        }
        if (usuarioRepository.existsByCorreo(usuario.getCorreo())) {
            throw new RuntimeException("El correo ya está registrado");
        }
        return usuarioRepository.save(usuario);
    }

    // RF-USR-02: Modificar Usuario
    @Override
    public Usuario actualizarUsuario(Long id, Usuario usuarioDetalles) {
        Usuario usuarioExistente = obtenerPorId(id);

        usuarioExistente.setNombres(usuarioDetalles.getNombres());
        usuarioExistente.setApellidos(usuarioDetalles.getApellidos());
        usuarioExistente.setDni(usuarioDetalles.getDni());
        usuarioExistente.setCorreo(usuarioDetalles.getCorreo());
        usuarioExistente.setTelefono(usuarioDetalles.getTelefono());
        usuarioExistente.setUsuario(usuarioDetalles.getUsuario());

        if (usuarioDetalles.getContrasena() != null && !usuarioDetalles.getContrasena().isEmpty()) {
            usuarioExistente.setContrasena(usuarioDetalles.getContrasena());
        }

        usuarioExistente.setArea(usuarioDetalles.getArea());
        usuarioExistente.setEstado(usuarioDetalles.getEstado());
        usuarioExistente.setRol(usuarioDetalles.getRol());

        return usuarioRepository.save(usuarioExistente);
    }
}
