package com.evaluacion01.tecsup.service;

import com.evaluacion01.tecsup.entity.Usuario;
import java.util.List;

public interface UsuarioService {
    List<Usuario> listarTodos();
    List<Usuario> buscar(String texto, String area, Integer idRol, Boolean estado);
    Usuario obtenerPorId(Long id);
    Usuario registrarUsuario(Usuario usuario, List<Integer> idsRolesAdicionales);
    Usuario actualizarUsuario(Long id, Usuario usuario, List<Integer> idsRolesAdicionales);
    Usuario cambiarEstado(Long id, boolean activo, Usuario usuarioLogueado);
}
