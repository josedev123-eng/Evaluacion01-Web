package com.evaluacion01.tecsup.service;

import com.evaluacion01.tecsup.entity.Usuario;
import java.util.List;

public interface UsuarioService {
    List<Usuario> listarTodos();
    List<Usuario> buscar(String texto, String area, Integer idRol, Boolean estado);
    Usuario obtenerPorId(Long id);
    Usuario registrarUsuario(Usuario usuario, List<Integer> idsRolesAdicionales, Usuario operador);
    Usuario actualizarUsuario(Long id, Usuario usuario, List<Integer> idsRolesAdicionales, Usuario operador);
    Usuario cambiarEstado(Long id, boolean activo, Usuario usuarioLogueado);
    void asignarRolAdicional(Long usuarioId, Long rolId, Usuario operador);
    void removerRolAdicional(Long usuarioId, Long rolId, Usuario operador);

    // Compatibilidad con la entrega anterior: sin operador se rechaza la modificación.
    @Deprecated
    default void asignarRolAdicional(Long usuarioId, Long rolId) {
        asignarRolAdicional(usuarioId, rolId, null);
    }

    @Deprecated
    default void removerRolAdicional(Long usuarioId, Long rolId) {
        removerRolAdicional(usuarioId, rolId, null);
    }
}
