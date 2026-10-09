package com.evaluacion01.tecsup.service;

import com.evaluacion01.tecsup.audit.ModuloAuditoria;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.evaluacion01.tecsup.entity.Permiso;
import com.evaluacion01.tecsup.entity.Rol;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.PermisoRepository;
import com.evaluacion01.tecsup.repository.RolRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RolService {

    // Roles que el sistema necesita siempre; schema.sql los crea si faltan.
    public static final List<String> ROLES_BASE = List.of("Administrador", "Médico", "Recepcionista");

    private final RolRepository rolRepository;
    private final PermisoRepository permisoRepository;
    private final AutorizacionService autorizacionService;
    private final AuditoriaService auditoriaService;

    public List<Rol> listar() {
        return rolRepository.findAll();
    }

    public List<Rol> listarActivos() {
        return rolRepository.findByEstadoTrue();
    }

    // Devuelve los roles base que no están en el catálogo (lista vacía si están todos).
    public List<String> rolesBaseFaltantes() {
        return ROLES_BASE.stream()
                .filter(nombre -> !rolRepository.existsByNombreIgnoreCase(nombre))
                .toList();
    }

    public Rol obtenerPorId(Integer idRol) {
        return rolRepository.findById(idRol)
                .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado: " + idRol));
    }

    public boolean perteneceAArea(Integer idRol, String area) {
        if (area == null || area.isBlank()) {
            return false;
        }
        return obtenerPorId(idRol).getArea().equalsIgnoreCase(area.trim());
    }

    public List<Permiso> listarPermisos() {
        return permisoRepository.findAll();
    }

    @Transactional
    public Rol guardar(Rol rol, Usuario operador) {
        String accion = rol.getIdRol() == null ? "CREAR_ROL" : "EDITAR_ROL";
        try {
            autorizacionService.exigirAdministrador(operador);
            if (rol.getNombre() == null || rol.getNombre().isBlank()) {
                throw new IllegalArgumentException("El nombre del rol es obligatorio");
            }
            rol.setNombre(rol.getNombre().trim());
            if (rol.getArea() == null || rol.getArea().isBlank()) {
                throw new IllegalArgumentException("El área del rol es obligatoria");
            }
            rol.setArea(rol.getArea().trim());
            rolRepository.findByNombreIgnoreCase(rol.getNombre()).ifPresent(existente -> {
                if (!existente.getIdRol().equals(rol.getIdRol())) {
                    throw new IllegalArgumentException("Ya existe un rol con el nombre '" + rol.getNombre() + "'");
                }
            });

            Rol destino;
            String detalle;
            if (rol.getIdRol() != null) {
                destino = obtenerPorId(rol.getIdRol());
                if (autorizacionService.esRolAdministrador(destino) && !autorizacionService.esRolAdministrador(rol)) {
                    throw new IllegalArgumentException("El rol Administrador es reservado y no se puede renombrar.");
                }
                List<String> campos = new java.util.ArrayList<>();
                if (!Objects.equals(destino.getNombre(), rol.getNombre())) campos.add("nombre");
                if (!Objects.equals(destino.getDescripcion(), rol.getDescripcion())) campos.add("descripcion");
                if (!Objects.equals(destino.getArea(), rol.getArea())) campos.add("area");
                detalle = "Campos modificados: " + campos;
            } else {
                destino = new Rol();
                detalle = "Rol creado sin permisos iniciales";
            }
            destino.setNombre(rol.getNombre());
            destino.setDescripcion(rol.getDescripcion());
            destino.setArea(rol.getArea());
            Rol guardado = rolRepository.saveAndFlush(destino);
            auditoriaService.registrarExito(operador, ModuloAuditoria.ROLES, accion, "Rol", guardado.getIdRol(), detalle);
            return guardado;
        } catch (RuntimeException e) {
            auditoriaService.registrarFalloOperacion(operador, ModuloAuditoria.ROLES, accion, "Rol", rol.getIdRol(), e);
            throw e;
        }
    }

    // Activa o desactiva de forma explícita (no alterna), igual que el estado de usuarios.
    // El rol Administrador nunca se desactiva: así siempre queda quien administre el sistema.
    @Transactional
    public Rol cambiarEstado(Integer idRol, boolean activo, Usuario operador) {
        String accion = activo ? "ACTIVAR_ROL" : "DESACTIVAR_ROL";
        try {
            autorizacionService.exigirAdministrador(operador);
            Rol rol = obtenerPorId(idRol);
            if (!activo && autorizacionService.esRolAdministrador(rol)) {
                throw new IllegalStateException("El rol Administrador es reservado y no se puede desactivar.");
            }
            boolean anterior = rol.isActivo();
            rol.setEstado(activo);
            Rol guardado = rolRepository.saveAndFlush(rol);
            auditoriaService.registrarExito(operador, ModuloAuditoria.ROLES, accion, "Rol", idRol,
                    "Estado: " + anterior + " -> " + activo);
            return guardado;
        } catch (RuntimeException e) {
            auditoriaService.registrarFalloOperacion(operador, ModuloAuditoria.ROLES, accion, "Rol", idRol, e);
            throw e;
        }
    }

    @Transactional
    public Rol asignarPermisos(Integer idRol, List<Integer> idsPermisos, Usuario operador) {
        try {
            autorizacionService.exigirAdministrador(operador);
            Rol rol = obtenerPorId(idRol);
            if (idsPermisos != null && idsPermisos.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("Uno o más permisos seleccionados no existen.");
            }
            Set<Permiso> permisos = (idsPermisos == null || idsPermisos.isEmpty())
                    ? new HashSet<>() : new HashSet<>(permisoRepository.findAllById(idsPermisos));
            if (idsPermisos != null && permisos.size() != new HashSet<>(idsPermisos).size()) {
                throw new IllegalArgumentException("Uno o más permisos seleccionados no existen.");
            }
            List<Integer> anteriores = rol.getPermisos().stream().map(Permiso::getIdPermiso).sorted().toList();
            List<Integer> nuevos = permisos.stream().map(Permiso::getIdPermiso).sorted().toList();
            rol.setPermisos(permisos);
            Rol guardado = rolRepository.saveAndFlush(rol);
            auditoriaService.registrarExito(operador, ModuloAuditoria.PERMISOS, "ASIGNAR_PERMISOS", "Rol", idRol,
                    "Permisos: " + anteriores + " -> " + nuevos);
            return guardado;
        } catch (RuntimeException e) {
            auditoriaService.registrarFalloOperacion(operador, ModuloAuditoria.PERMISOS, "ASIGNAR_PERMISOS", "Rol", idRol, e);
            throw e;
        }
    }
}
