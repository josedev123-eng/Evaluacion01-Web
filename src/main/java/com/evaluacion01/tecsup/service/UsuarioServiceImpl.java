package com.evaluacion01.tecsup.service;

import com.evaluacion01.tecsup.entity.Rol;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.RolRepository;
import com.evaluacion01.tecsup.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final RolRepository rolRepository;
    private final AuditoriaService auditoriaService;

    @Autowired
    public UsuarioServiceImpl(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                              RolRepository rolRepository, AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.rolRepository = rolRepository;
        this.auditoriaService = auditoriaService;
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
        usuario.setContrasena(passwordEncoder.encode(usuario.getContrasena()));
        usuario.setFechaRegistro(LocalDateTime.now());
        if (usuario.getEstado() == null) {
            usuario.setEstado(true);
        }
        Usuario guardado = usuarioRepository.save(usuario);

        // RF-AUD-01: alta de usuario.
        auditoriaService.registrar(AuditoriaService.MODULO_USUARIOS, "CREAR_USUARIO", "Usuario",
                guardado.getIdUsuario(),
                "Usuario creado: " + guardado.getNombres() + " " + guardado.getApellidos()
                        + " (" + guardado.getUsuario() + ", " + guardado.getCorreo() + ")"
                        + " con rol " + nombreRol(guardado));

        return guardado;
    }

    @Override
    public Usuario actualizarUsuario(Long id, Usuario usuarioActualizado) {
        Usuario usuarioExistente = obtenerPorId(id);

        Map<String, String> antes = resumenCampos(usuarioExistente);
        boolean cambioContrasena = usuarioActualizado.getContrasena() != null
                && !usuarioActualizado.getContrasena().isEmpty();

        usuarioExistente.setNombres(usuarioActualizado.getNombres());
        usuarioExistente.setApellidos(usuarioActualizado.getApellidos());
        usuarioExistente.setDni(usuarioActualizado.getDni());
        usuarioExistente.setCorreo(usuarioActualizado.getCorreo());
        usuarioExistente.setTelefono(usuarioActualizado.getTelefono());
        usuarioExistente.setUsuario(usuarioActualizado.getUsuario());

        if (cambioContrasena) {
            usuarioExistente.setContrasena(passwordEncoder.encode(usuarioActualizado.getContrasena()));
        }

        usuarioExistente.setArea(usuarioActualizado.getArea());

        if (usuarioActualizado.getEstado() != null) {
            usuarioExistente.setEstado(usuarioActualizado.getEstado());
        }

        if (usuarioActualizado.getRol() != null && usuarioActualizado.getRol().getIdRol() != null) {
            usuarioExistente.setRol(usuarioActualizado.getRol());
        }

        Usuario guardado = usuarioRepository.save(usuarioExistente);

        // RF-AUD-01: modificación de usuario con detalle de los campos alterados.
        String detalle = diferenciar(antes, resumenCampos(guardado));
        if (cambioContrasena) {
            detalle = detalle + "; contrasena: '****' -> '****' (actualizada)";
        }
        auditoriaService.registrar(AuditoriaService.MODULO_USUARIOS, "EDITAR_USUARIO", "Usuario",
                guardado.getIdUsuario(), detalle);

        return guardado;
    }

    @Override
    public Usuario cambiarEstado(Long id) {
        Usuario usuario = obtenerPorId(id);
        boolean estadoAnterior = Boolean.TRUE.equals(usuario.getEstado());
        usuario.setEstado(!estadoAnterior);
        Usuario guardado = usuarioRepository.save(usuario);

        // RF-AUD-01: activación / desactivación de cuentas.
        auditoriaService.registrar(AuditoriaService.MODULO_USUARIOS, "CAMBIAR_ESTADO_USUARIO", "Usuario",
                guardado.getIdUsuario(),
                "Estado cambiado de " + (estadoAnterior ? "activo" : "inactivo") + " a "
                        + (estadoAnterior ? "inactivo" : "activo"));

        return guardado;
    }

    private Map<String, String> resumenCampos(Usuario usuario) {
        Map<String, String> resumen = new LinkedHashMap<>();
        resumen.put("nombres", usuario.getNombres());
        resumen.put("apellidos", usuario.getApellidos());
        resumen.put("dni", usuario.getDni());
        resumen.put("correo", usuario.getCorreo());
        resumen.put("telefono", usuario.getTelefono());
        resumen.put("usuario", usuario.getUsuario());
        resumen.put("area", usuario.getArea());
        resumen.put("estado", usuario.getEstado() == null ? null
                : (Boolean.TRUE.equals(usuario.getEstado()) ? "activo" : "inactivo"));
        resumen.put("rol", nombreRol(usuario));
        return resumen;
    }

    private String diferenciar(Map<String, String> antes, Map<String, String> despues) {
        List<String> cambios = new ArrayList<>();
        for (String campo : despues.keySet()) {
            String valorAnterior = antes.get(campo);
            String valorNuevo = despues.get(campo);
            if (!Objects.equals(valorAnterior, valorNuevo)) {
                cambios.add(campo + ": '" + valorAnterior + "' -> '" + valorNuevo + "'");
            }
        }
        return cambios.isEmpty() ? "Sin cambios de campos" : String.join("; ", cambios);
    }

    // El formulario sólo envía el id del rol, por lo que el nombre se resuelve
    // desde el repositorio. Nunca debe romper el flujo principal.
    private String nombreRol(Usuario usuario) {
        try {
            Rol rol = usuario.getRol();
            if (rol == null) {
                return null;
            }
            if (rol.getNombre() != null && !rol.getNombre().isBlank()) {
                return rol.getNombre();
            }
            if (rol.getIdRol() == null) {
                return null;
            }
            return rolRepository.findById(rol.getIdRol())
                    .map(Rol::getNombre)
                    .orElse("ID " + rol.getIdRol());
        } catch (Exception e) {
            return usuario.getRol() == null ? null : "ID " + usuario.getRol().getIdRol();
        }
    }
}
