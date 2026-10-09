package com.evaluacion01.tecsup.service;

import com.evaluacion01.tecsup.audit.ModuloAuditoria;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class UsuarioServiceImpl implements UsuarioService {

    private static final Pattern CORREO = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    private static final Pattern DNI = Pattern.compile("^\\d{8}$");
    private static final Pattern TELEFONO = Pattern.compile("^\\+?[\\d ]{6,20}$");
    private static final Pattern NOMBRE_USUARIO = Pattern.compile("^[A-Za-z0-9._-]{3,50}$");
    private static final int LONGITUD_MINIMA_CONTRASENA = 6;

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final AutorizacionService autorizacionService;
    private final AuditoriaService auditoriaService;

    @Autowired
    public UsuarioServiceImpl(UsuarioRepository usuarioRepository, RolRepository rolRepository,
                              PasswordEncoder passwordEncoder, AutorizacionService autorizacionService,
                              AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
        this.autorizacionService = autorizacionService;
        this.auditoriaService = auditoriaService;
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
        try {
            validarDatos(usuario, null);
            Rol principal = obtenerRolPrincipal(usuario);
            Set<Rol> roles = armarRoles(principal, idsRolesAdicionales, usuario.getArea(), List.of());
            autorizacionService.validarAsignacionRoles(operador, null, roles);
            if (usuario.getContrasena() == null || usuario.getContrasena().isBlank()) {
                throw new IllegalArgumentException("La contraseña es obligatoria para registrar un usuario.");
            }
            validarContrasena(usuario.getContrasena());
            // No persistir id, estado, tokens ni asociaciones enviados fuera del formulario.
            Usuario nuevo = new Usuario();
            copiarDatosPersonales(usuario, nuevo);
            nuevo.setContrasena(passwordEncoder.encode(usuario.getContrasena()));
            nuevo.setFechaRegistro(LocalDateTime.now());
            nuevo.setEstado(true);
            nuevo.setRol(principal);
            nuevo.setRoles(roles);
            Usuario guardado = usuarioRepository.saveAndFlush(nuevo);
            auditoriaService.registrarExito(operador, ModuloAuditoria.USUARIOS, "CREAR_USUARIO", "Usuario",
                    guardado.getIdUsuario(), "Cuenta creada; rol principal=" + principal.getIdRol() + "; roles=" + idsRoles(roles));
            return guardado;
        } catch (RuntimeException e) {
            auditoriaService.registrarFalloOperacion(operador, ModuloAuditoria.USUARIOS, "CREAR_USUARIO", "Usuario", null, e);
            throw e;
        }
    }

    @Override
    @Transactional
    public Usuario actualizarUsuario(Long id, Usuario usuarioActualizado, List<Integer> idsRolesAdicionales,
                                     Usuario operador) {
        try {
            Usuario usuarioExistente = obtenerPorId(id);
            autorizacionService.validarEdicionUsuario(operador, usuarioExistente);
            validarDatos(usuarioActualizado, id);
            if (usuarioActualizado.getContrasena() != null && !usuarioActualizado.getContrasena().isEmpty()) {
                validarContrasena(usuarioActualizado.getContrasena());
            }
            Rol principal = obtenerRolPrincipal(usuarioActualizado);
            Set<Rol> roles = armarRoles(principal, idsRolesAdicionales, usuarioActualizado.getArea(),
                    idsRoles(usuarioExistente.getRolesAsignados()));
            autorizacionService.validarAsignacionRoles(operador, usuarioExistente, roles);
            validarConservacionAdministrador(usuarioExistente, roles);
            String detalle = detallarCambios(usuarioExistente, usuarioActualizado, principal, roles);
            copiarDatosPersonales(usuarioActualizado, usuarioExistente);

            if (usuarioActualizado.getContrasena() != null && !usuarioActualizado.getContrasena().isEmpty()) {
                usuarioExistente.setContrasena(passwordEncoder.encode(usuarioActualizado.getContrasena()));
            }

            // RF-USR-03: editar no cambia el estado; solo cambiarEstado() puede hacerlo.
            usuarioExistente.setRol(principal);
            usuarioExistente.setRoles(roles);
            Usuario guardado = usuarioRepository.saveAndFlush(usuarioExistente);
            auditoriaService.registrarExito(operador, ModuloAuditoria.USUARIOS, "EDITAR_USUARIO", "Usuario", id, detalle);
            return guardado;
        } catch (RuntimeException e) {
            auditoriaService.registrarFalloOperacion(operador, ModuloAuditoria.USUARIOS, "EDITAR_USUARIO", "Usuario", id, e);
            throw e;
        }
    }

    // RF-USR-03: activa o desactiva de forma explícita (no alterna), así un doble clic
    // o un reenvío del formulario no revierte la acción.
    @Override
    @Transactional
    public Usuario cambiarEstado(Long id, boolean activo, Usuario usuarioLogueado) {
        String accion = activo ? "ACTIVAR_USUARIO" : "DESACTIVAR_USUARIO";
        try {
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
            boolean anterior = Boolean.TRUE.equals(usuario.getEstado());
            usuario.setEstado(activo);
            Usuario guardado = usuarioRepository.saveAndFlush(usuario);
            auditoriaService.registrarExito(usuarioLogueado, ModuloAuditoria.USUARIOS, accion, "Usuario", id,
                    "Estado: " + anterior + " -> " + activo);
            return guardado;
        } catch (RuntimeException e) {
            auditoriaService.registrarFalloOperacion(usuarioLogueado, ModuloAuditoria.USUARIOS, accion, "Usuario", id, e);
            throw e;
        }
    }

    // RF-USR-04: el rol principal siempre forma parte de los roles asignados. Todos los
    // roles deben pertenecer al área del usuario, igual que la regla del rol principal.
    // Un rol inactivo no admite asignaciones nuevas: solo lo conserva quien ya lo tenía.
    private Set<Rol> armarRoles(Rol principal, List<Integer> idsRolesAdicionales, String area,
                                List<Integer> idsRolesYaAsignados) {
        validarArea(principal, area);
        validarRolAsignable(principal, idsRolesYaAsignados);
        Set<Rol> roles = new LinkedHashSet<>();
        roles.add(principal);
        if (idsRolesAdicionales != null) {
            for (Integer idRol : idsRolesAdicionales) {
                if (idRol == null || idRol.equals(principal.getIdRol())) {
                    continue;
                }
                Rol rol = obtenerRol(idRol);
                validarArea(rol, area);
                validarRolAsignable(rol, idsRolesYaAsignados);
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

    private void validarRolAsignable(Rol rol, List<Integer> idsRolesYaAsignados) {
        if (!rol.isActivo() && !idsRolesYaAsignados.contains(rol.getIdRol())) {
            throw new IllegalArgumentException("El rol '" + rol.getNombre() + "' está inactivo y no se puede asignar.");
        }
    }

    private void validarArea(Rol rol, String area) {
        if (area == null || rol.getArea() == null || !rol.getArea().equalsIgnoreCase(area.trim())) {
            throw new IllegalArgumentException("El rol '" + rol.getNombre() + "' no corresponde al área elegida.");
        }
    }

    // Normaliza los textos del formulario y valida obligatorios, formatos y duplicados.
    // idExcluido es el propio usuario al editar, para que conservar sus datos no cuente como duplicado.
    private void validarDatos(Usuario usuario, Long idExcluido) {
        usuario.setNombres(obligatorio(usuario.getNombres(), "nombres", 100));
        usuario.setApellidos(obligatorio(usuario.getApellidos(), "apellidos", 100));
        usuario.setCorreo(obligatorio(usuario.getCorreo(), "correo", 100).toLowerCase());
        usuario.setUsuario(obligatorio(usuario.getUsuario(), "usuario", 50));
        usuario.setArea(obligatorio(usuario.getArea(), "área", 100));
        usuario.setDni(opcional(usuario.getDni()));
        usuario.setTelefono(opcional(usuario.getTelefono()));

        if (!CORREO.matcher(usuario.getCorreo()).matches()) {
            throw new IllegalArgumentException("El correo no tiene un formato válido.");
        }
        if (!NOMBRE_USUARIO.matcher(usuario.getUsuario()).matches()) {
            throw new IllegalArgumentException("El usuario debe tener de 3 a 50 caracteres: letras, números, punto, guion o guion bajo.");
        }
        if (usuario.getDni() != null && !DNI.matcher(usuario.getDni()).matches()) {
            throw new IllegalArgumentException("El DNI debe tener 8 dígitos.");
        }
        if (usuario.getTelefono() != null && !TELEFONO.matcher(usuario.getTelefono()).matches()) {
            throw new IllegalArgumentException("El teléfono solo puede tener dígitos, espacios y un + inicial.");
        }

        boolean usuarioRepetido = idExcluido == null
                ? usuarioRepository.existsByUsuarioIgnoreCase(usuario.getUsuario())
                : usuarioRepository.existsByUsuarioIgnoreCaseAndIdUsuarioNot(usuario.getUsuario(), idExcluido);
        if (usuarioRepetido) {
            throw new IllegalArgumentException("Ya existe un usuario con ese nombre de usuario.");
        }
        boolean correoRepetido = idExcluido == null
                ? usuarioRepository.existsByCorreoIgnoreCase(usuario.getCorreo())
                : usuarioRepository.existsByCorreoIgnoreCaseAndIdUsuarioNot(usuario.getCorreo(), idExcluido);
        if (correoRepetido) {
            throw new IllegalArgumentException("Ya existe un usuario con ese correo.");
        }
        if (usuario.getDni() != null) {
            boolean dniRepetido = idExcluido == null
                    ? usuarioRepository.existsByDni(usuario.getDni())
                    : usuarioRepository.existsByDniAndIdUsuarioNot(usuario.getDni(), idExcluido);
            if (dniRepetido) {
                throw new IllegalArgumentException("Ya existe un usuario con ese DNI.");
            }
        }
    }

    private String obligatorio(String valor, String campo, int longitudMaxima) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El campo " + campo + " es obligatorio.");
        }
        String limpio = valor.trim();
        if (limpio.length() > longitudMaxima) {
            throw new IllegalArgumentException("El campo " + campo + " admite como máximo " + longitudMaxima + " caracteres.");
        }
        return limpio;
    }

    // DNI y teléfono vacíos se guardan como NULL: dni es UNIQUE y dos "" chocarían entre sí.
    private String opcional(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }

    private void validarContrasena(String contrasena) {
        if (contrasena.length() < LONGITUD_MINIMA_CONTRASENA) {
            throw new IllegalArgumentException("La contraseña debe tener al menos " + LONGITUD_MINIMA_CONTRASENA + " caracteres.");
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

    private List<Integer> idsRoles(Set<Rol> roles) {
        return roles.stream().map(Rol::getIdRol).sorted().toList();
    }

    private String detallarCambios(Usuario anterior, Usuario recibido, Rol principal, Set<Rol> nuevosRoles) {
        List<String> campos = new ArrayList<>();
        if (!Objects.equals(anterior.getNombres(), recibido.getNombres())) campos.add("nombres");
        if (!Objects.equals(anterior.getApellidos(), recibido.getApellidos())) campos.add("apellidos");
        if (!Objects.equals(anterior.getDni(), recibido.getDni())) campos.add("dni");
        if (!Objects.equals(anterior.getCorreo(), recibido.getCorreo())) campos.add("correo");
        if (!Objects.equals(anterior.getTelefono(), recibido.getTelefono())) campos.add("telefono");
        if (!Objects.equals(anterior.getUsuario(), recibido.getUsuario())) campos.add("usuario");
        if (!Objects.equals(anterior.getArea(), recibido.getArea().trim())) campos.add("area");
        if (recibido.getContrasena() != null && !recibido.getContrasena().isEmpty()) campos.add("contrasena");
        if (!Objects.equals(anterior.getRol().getIdRol(), principal.getIdRol())) campos.add("rol_principal");
        List<Integer> antes = idsRoles(anterior.getRolesAsignados());
        List<Integer> despues = idsRoles(nuevosRoles);
        if (!antes.equals(despues)) campos.add("roles");
        // Solo nombres de campos e IDs: no duplicar DNI, correo, teléfono ni claves en el historial.
        return "Campos modificados: " + campos + "; rol principal: " + anterior.getRol().getIdRol()
                + " -> " + principal.getIdRol() + "; roles: " + antes + " -> " + despues;
    }
}
