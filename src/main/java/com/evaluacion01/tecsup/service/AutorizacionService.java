package com.evaluacion01.tecsup.service;

import com.evaluacion01.tecsup.entity.Rol;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AutorizacionService {

    public static final String ROL_ADMINISTRADOR = "Administrador";

    private final UsuarioRepository usuarioRepository;

    // RF-ROL-03: unión de los permisos del principal y de todos los roles adicionales.
    // RF-ROL-04: la identidad y los roles se recuperan de la BD, nunca del formulario
    // ni de una copia antigua de la sesión. Una cuenta inactiva no tiene acceso.
    public boolean tienePermiso(Usuario usuario, String modulo, String nombrePermiso) {
        return tienePermisoActual(obtenerUsuarioActivo(usuario), modulo, nombrePermiso);
    }

    public boolean esAdministrador(Usuario usuario) {
        return tieneRolAdministrador(obtenerUsuarioActivo(usuario));
    }

    public boolean esRolAdministrador(Rol rol) {
        return rol != null && rol.getNombre() != null
                && ROL_ADMINISTRADOR.equalsIgnoreCase(rol.getNombre().trim());
    }

    // Para cuentas obtenidas del repositorio, incluso si están inactivas.
    public boolean tieneRolAdministrador(Usuario usuario) {
        return usuario != null && usuario.getRolesAsignados().stream().anyMatch(this::esRolAdministrador);
    }

    // Redirección posterior al login: cada usuario entra al módulo que le corresponde.
    // El Administrador ve el panel con todos los módulos; quien gestiona o consulta
    // usuarios o roles entra directo a ese módulo; el resto de roles va al panel.
    public String rutaInicial(Usuario usuario) {
        Usuario actual = obtenerUsuarioActivo(usuario);
        if (actual == null) {
            return "/login";
        }
        if (tieneRolAdministrador(actual)) {
            return "/dashboard";
        }
        if (tienePermisoActual(actual, "usuarios", "VER")) {
            return "/usuarios";
        }
        if (tienePermisoActual(actual, "roles", "VER")) {
            return "/roles";
        }
        return "/dashboard";
    }

    public void exigirAdministrador(Usuario operador) {
        if (!esAdministrador(operador)) {
            throw new AccessDeniedException("Solo un Administrador puede gestionar roles y permisos.");
        }
    }

    public void validarEdicionUsuario(Usuario operador, Usuario destino) {
        Usuario actual = obtenerUsuarioActivo(operador);
        if (!tienePermisoActual(actual, "usuarios", "EDITAR")) {
            throw new AccessDeniedException("No tienes permiso para modificar usuarios.");
        }
        if (!tieneRolAdministrador(actual) && tieneRolAdministrador(destino)) {
            throw new AccessDeniedException("Solo un Administrador puede modificar cuentas administrativas.");
        }
        if (!tieneRolAdministrador(actual) && tienePermisosSuperiores(destino.getRolesAsignados(), actual)) {
            throw new AccessDeniedException("No puedes modificar cuentas con permisos superiores a los tuyos.");
        }
    }

    public boolean puedeEditarUsuario(Usuario operador, Usuario destino) {
        Usuario actual = obtenerUsuarioActivo(operador);
        return destino != null && tienePermisoActual(actual, "usuarios", "EDITAR")
                && (tieneRolAdministrador(actual) || (!tieneRolAdministrador(destino)
                && !tienePermisosSuperiores(destino.getRolesAsignados(), actual)));
    }

    public void validarAsignacionRoles(Usuario operador, Usuario existente, Set<Rol> nuevosRoles) {
        Usuario actual = obtenerUsuarioActivo(operador);
        String permiso = existente == null ? "CREAR" : "EDITAR";
        if (!tienePermisoActual(actual, "usuarios", permiso)) {
            throw new AccessDeniedException("No tienes permiso para "
                    + (existente == null ? "crear" : "editar") + " usuarios.");
        }
        if (tieneRolAdministrador(actual)) {
            return;
        }
        if (tieneRolAdministrador(existente)) {
            throw new AccessDeniedException("Solo un Administrador puede modificar cuentas administrativas.");
        }
        if (nuevosRoles.stream().anyMatch(this::esRolAdministrador)) {
            throw new AccessDeniedException("Solo un Administrador puede asignar el rol Administrador.");
        }
        if (existente != null && Objects.equals(actual.getIdUsuario(), existente.getIdUsuario())
                && !idsRoles(existente.getRolesAsignados()).equals(idsRoles(nuevosRoles))) {
            throw new AccessDeniedException("No puedes cambiar tus propios roles. Solicita el cambio a un Administrador.");
        }
        // Un operador no puede crear una cuenta alternativa con permisos que él no posee.
        Set<Integer> anteriores = existente == null ? Set.of() : idsRoles(existente.getRolesAsignados());
        Set<Rol> rolesNuevos = nuevosRoles.stream()
                .filter(rol -> !anteriores.contains(rol.getIdRol()))
                .collect(Collectors.toSet());
        if (tienePermisosSuperiores(rolesNuevos, actual)) {
            throw new AccessDeniedException("No puedes asignar roles con permisos superiores a los tuyos.");
        }
    }

    private Usuario obtenerUsuarioActivo(Usuario usuario) {
        if (usuario == null || usuario.getIdUsuario() == null) {
            return null;
        }
        return usuarioRepository.findById(usuario.getIdUsuario())
                .filter(actual -> Boolean.TRUE.equals(actual.getEstado()))
                .orElse(null);
    }

    private boolean tienePermisoActual(Usuario actual, String modulo, String nombrePermiso) {
        if (actual == null || modulo == null || nombrePermiso == null) {
            return false;
        }
        if (tieneRolAdministrador(actual)) {
            return true;
        }
        // Gestionar el catálogo o los permisos permitiría escalar privilegios.
        if (esPermisoReservado(modulo, nombrePermiso)) {
            return false;
        }
        // Un rol inactivo sigue asignado al usuario, pero no aporta ninguno de sus permisos.
        return actual.getRolesAsignados().stream()
                .filter(Rol::isActivo)
                .flatMap(rol -> rol.getPermisos().stream())
                .anyMatch(p -> modulo.equalsIgnoreCase(p.getModulo())
                        && nombrePermiso.equalsIgnoreCase(p.getNombre()));
    }

    private Set<Integer> idsRoles(Set<Rol> roles) {
        return roles.stream().map(Rol::getIdRol).collect(Collectors.toSet());
    }

    private boolean tienePermisosSuperiores(Set<Rol> roles, Usuario actual) {
        return roles.stream().flatMap(rol -> rol.getPermisos().stream())
                .filter(p -> !esPermisoReservado(p.getModulo(), p.getNombre()))
                .anyMatch(p -> !tienePermisoActual(actual, p.getModulo(), p.getNombre()));
    }

    private boolean esPermisoReservado(String modulo, String permiso) {
        return ("roles".equalsIgnoreCase(modulo) && "EDITAR".equalsIgnoreCase(permiso))
                || "auditoria".equalsIgnoreCase(modulo);
    }
}
