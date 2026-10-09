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

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final AutorizacionService autorizacionService;

    @Autowired
    public UsuarioServiceImpl(UsuarioRepository usuarioRepository, RolRepository rolRepository,
                              PasswordEncoder passwordEncoder, AutorizacionService autorizacionService) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
        this.autorizacionService = autorizacionService;
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
    public Usuario registrarUsuario(Usuario usuario, List<Integer> idsRolesAdicionales, Usuario operador) {
        Rol principal = obtenerRolPrincipal(usuario);
        Set<Rol> roles = armarRoles(principal, idsRolesAdicionales, usuario.getArea());
        autorizacionService.validarAsignacionRoles(operador, null, roles);
        if (usuario.getContrasena() == null || usuario.getContrasena().isBlank()) {
            throw new IllegalArgumentException("La contraseña es obligatoria para registrar un usuario.");
        }
        // No persistir id, estado, tokens ni asociaciones enviados fuera del formulario.
        Usuario nuevo = new Usuario();
        copiarDatosPersonales(usuario, nuevo);
        nuevo.setContrasena(passwordEncoder.encode(usuario.getContrasena()));
        nuevo.setFechaRegistro(LocalDateTime.now());
        nuevo.setEstado(true);
        nuevo.setRol(principal);
        nuevo.setRoles(roles);
        return usuarioRepository.save(nuevo);
    }

    @Override
    @Transactional
    public Usuario actualizarUsuario(Long id, Usuario usuarioActualizado, List<Integer> idsRolesAdicionales,
                                     Usuario operador) {
        Usuario usuarioExistente = obtenerPorId(id);
        autorizacionService.validarEdicionUsuario(operador, usuarioExistente);
        Rol principal = obtenerRolPrincipal(usuarioActualizado);
        Set<Rol> roles = armarRoles(principal, idsRolesAdicionales, usuarioActualizado.getArea());
        autorizacionService.validarAsignacionRoles(operador, usuarioExistente, roles);
        validarConservacionAdministrador(usuarioExistente, roles);
        copiarDatosPersonales(usuarioActualizado, usuarioExistente);

        if (usuarioActualizado.getContrasena() != null && !usuarioActualizado.getContrasena().isEmpty()) {
            usuarioExistente.setContrasena(passwordEncoder.encode(usuarioActualizado.getContrasena()));
        }

        // RF-USR-03: el estado ya NO se toma del formulario de edición. El formulario no
        // envía "estado" y la entidad lo inicializa en true, así que antes editar un
        // usuario inactivo lo volvía a activar sin querer. El estado solo cambia con
        // cambiarEstado().

        usuarioExistente.setRol(principal);
        usuarioExistente.setRoles(roles);

        return usuarioRepository.save(usuarioExistente);
    }

    // RF-USR-03: activa o desactiva de forma explícita (no alterna), así un doble clic
    // o un reenvío del formulario no revierte la acción.
    @Override
    @Transactional
    public Usuario cambiarEstado(Long id, boolean activo, Usuario usuarioLogueado) {
        Usuario usuario = obtenerPorId(id);
        autorizacionService.validarEdicionUsuario(usuarioLogueado, usuario);
        if (!activo) {
            if (usuarioLogueado != null && usuario.getIdUsuario().equals(usuarioLogueado.getIdUsuario())) {
                throw new IllegalStateException("No puedes desactivar tu propia cuenta.");
            }
            if (Boolean.TRUE.equals(usuario.getEstado()) && autorizacionService.tieneRolAdministrador(usuario)
                    && usuarioRepository.contarActivosConRol(AutorizacionService.ROL_ADMINISTRADOR) <= 1) {
                throw new IllegalStateException("No se puede desactivar al último administrador activo.");
            }
        }
        usuario.setEstado(activo);
        return usuarioRepository.save(usuario);
    }

    // RF-USR-04: el rol principal siempre forma parte de los roles asignados. Todos los
    // roles deben pertenecer al área del usuario, igual que la regla del rol principal.
    private Set<Rol> armarRoles(Rol principal, List<Integer> idsRolesAdicionales, String area) {
        validarArea(principal, area);
        Set<Rol> roles = new LinkedHashSet<>();
        roles.add(principal);
        if (idsRolesAdicionales != null) {
            for (Integer idRol : idsRolesAdicionales) {
                if (idRol == null || idRol.equals(principal.getIdRol())) {
                    continue;
                }
                Rol rol = obtenerRol(idRol);
                validarArea(rol, area);
                roles.add(rol);
            }
        }
        return roles;
    }

    private Rol obtenerRol(Integer idRol) {
        return rolRepository.findById(idRol)
                .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado: " + idRol));
    }

    private Rol obtenerRolPrincipal(Usuario usuario) {
        if (usuario.getRol() == null || usuario.getRol().getIdRol() == null) {
            throw new IllegalArgumentException("Debes seleccionar un rol principal.");
        }
        return obtenerRol(usuario.getRol().getIdRol());
    }

    private void validarArea(Rol rol, String area) {
        if (area == null || rol.getArea() == null || !rol.getArea().equalsIgnoreCase(area.trim())) {
            throw new IllegalArgumentException("El rol '" + rol.getNombre() + "' no corresponde al área elegida.");
        }
    }

    private void validarConservacionAdministrador(Usuario existente, Set<Rol> nuevosRoles) {
        if (Boolean.TRUE.equals(existente.getEstado()) && autorizacionService.tieneRolAdministrador(existente)
                && nuevosRoles.stream().noneMatch(autorizacionService::esRolAdministrador)
                && usuarioRepository.contarActivosConRol(AutorizacionService.ROL_ADMINISTRADOR) <= 1) {
            throw new IllegalStateException("No se puede quitar el rol al último administrador activo.");
        }
    }

    private void copiarDatosPersonales(Usuario origen, Usuario destino) {
        destino.setNombres(origen.getNombres());
        destino.setApellidos(origen.getApellidos());
        destino.setDni(origen.getDni());
        destino.setCorreo(origen.getCorreo());
        destino.setTelefono(origen.getTelefono());
        destino.setUsuario(origen.getUsuario());
        destino.setArea(origen.getArea().trim());
    }
}
