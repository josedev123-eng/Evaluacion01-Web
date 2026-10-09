package com.evaluacion01.tecsup.service;

import com.evaluacion01.tecsup.entity.Rol;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.RolRepository;
import com.evaluacion01.tecsup.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class UsuarioServiceImpl implements UsuarioService {

    private static final String ROL_ADMINISTRADOR = "Administrador";

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UsuarioServiceImpl(UsuarioRepository usuarioRepository, RolRepository rolRepository,
                              PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    // RF-USR-05: los textos vacíos se tratan como "sin filtro"
    @Override
    public List<Usuario> buscar(String texto, String area, Integer idRol, Boolean estado) {
        String patron = (texto == null || texto.isBlank()) ? null : "%" + texto.trim().toLowerCase() + "%";
        String areaFiltro = (area == null || area.isBlank()) ? null : area.trim();
        return usuarioRepository.buscar(patron, areaFiltro, idRol, estado);
    }

    @Override
    public Usuario obtenerPorId(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + id));
    }

    @Override
    @Transactional
    public Usuario registrarUsuario(Usuario usuario, List<Integer> idsRolesAdicionales) {
        usuario.setContrasena(passwordEncoder.encode(usuario.getContrasena()));
        usuario.setFechaRegistro(LocalDateTime.now());
        if (usuario.getEstado() == null) {
            usuario.setEstado(true);
        }
        Rol principal = obtenerRol(usuario.getRol().getIdRol());
        usuario.setRol(principal);
        usuario.setRoles(armarRoles(principal, idsRolesAdicionales, usuario.getArea()));
        return usuarioRepository.save(usuario);
    }

    @Override
    @Transactional
    public Usuario actualizarUsuario(Long id, Usuario usuarioActualizado, List<Integer> idsRolesAdicionales) {
        Usuario usuarioExistente = obtenerPorId(id);

        usuarioExistente.setNombres(usuarioActualizado.getNombres());
        usuarioExistente.setApellidos(usuarioActualizado.getApellidos());
        usuarioExistente.setDni(usuarioActualizado.getDni());
        usuarioExistente.setCorreo(usuarioActualizado.getCorreo());
        usuarioExistente.setTelefono(usuarioActualizado.getTelefono());
        usuarioExistente.setUsuario(usuarioActualizado.getUsuario());

        if (usuarioActualizado.getContrasena() != null && !usuarioActualizado.getContrasena().isEmpty()) {
            usuarioExistente.setContrasena(passwordEncoder.encode(usuarioActualizado.getContrasena()));
        }

        usuarioExistente.setArea(usuarioActualizado.getArea());

        // RF-USR-03: el estado ya NO se toma del formulario de edición. El formulario no
        // envía "estado" y la entidad lo inicializa en true, así que antes editar un
        // usuario inactivo lo volvía a activar sin querer. El estado solo cambia con
        // cambiarEstado().

        Rol principal = obtenerRol(usuarioActualizado.getRol().getIdRol());
        usuarioExistente.setRol(principal);
        usuarioExistente.setRoles(armarRoles(principal, idsRolesAdicionales, usuarioExistente.getArea()));

        return usuarioRepository.save(usuarioExistente);
    }

    // RF-USR-03: activa o desactiva de forma explícita (no alterna), así un doble clic
    // o un reenvío del formulario no revierte la acción.
    @Override
    @Transactional
    public Usuario cambiarEstado(Long id, boolean activo, Usuario usuarioLogueado) {
        Usuario usuario = obtenerPorId(id);
        if (!activo) {
            if (usuarioLogueado != null && usuario.getIdUsuario().equals(usuarioLogueado.getIdUsuario())) {
                throw new IllegalStateException("No puedes desactivar tu propia cuenta.");
            }
            if (Boolean.TRUE.equals(usuario.getEstado()) && esAdministrador(usuario)
                    && usuarioRepository.countByEstadoTrueAndRol_NombreIgnoreCase(ROL_ADMINISTRADOR) <= 1) {
                throw new IllegalStateException("No se puede desactivar al último administrador activo.");
            }
        }
        usuario.setEstado(activo);
        return usuarioRepository.save(usuario);
    }

    // RF-USR-04: el rol principal siempre forma parte de los roles asignados. Todos los
    // roles deben pertenecer al área del usuario, igual que la regla del rol principal.
    private Set<Rol> armarRoles(Rol principal, List<Integer> idsRolesAdicionales, String area) {
        Set<Rol> roles = new LinkedHashSet<>();
        roles.add(principal);
        if (idsRolesAdicionales != null) {
            for (Integer idRol : idsRolesAdicionales) {
                if (idRol == null || idRol.equals(principal.getIdRol())) {
                    continue;
                }
                Rol rol = obtenerRol(idRol);
                if (area == null || !rol.getArea().equalsIgnoreCase(area.trim())) {
                    throw new IllegalArgumentException("El rol '" + rol.getNombre() + "' no corresponde al área elegida.");
                }
                roles.add(rol);
            }
        }
        return roles;
    }

    private Rol obtenerRol(Integer idRol) {
        return rolRepository.findById(idRol)
                .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado: " + idRol));
    }

    private boolean esAdministrador(Usuario usuario) {
        return usuario.getRol() != null && ROL_ADMINISTRADOR.equalsIgnoreCase(usuario.getRol().getNombre());
    }
}
