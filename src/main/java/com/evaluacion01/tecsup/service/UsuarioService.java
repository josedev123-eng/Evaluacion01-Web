package com.evaluacion01.tecsup.service;

import com.evaluacion01.tecsup.entity.Usuario;
import java.util.List;

public interface UsuarioService {
    List<Usuario> listarTodos();
    Usuario obtenerPorId(Long id);
    Usuario registrarUsuario(Usuario usuario);
    Usuario actualizarUsuario(Long id, Usuario usuarioDetalles);
}
