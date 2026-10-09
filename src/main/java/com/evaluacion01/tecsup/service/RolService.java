package com.evaluacion01.tecsup.service;

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

    private final RolRepository rolRepository;
    private final PermisoRepository permisoRepository;
    private final AutorizacionService autorizacionService;

    public List<Rol> listar() {
        return rolRepository.findAll();
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

        if (rol.getIdRol() != null) {
            Rol actual = obtenerPorId(rol.getIdRol());
            if (autorizacionService.esRolAdministrador(actual)
                    && !autorizacionService.esRolAdministrador(rol)) {
                throw new IllegalArgumentException("El rol Administrador es reservado y no se puede renombrar.");
            }
            actual.setNombre(rol.getNombre());
            actual.setDescripcion(rol.getDescripcion());
            actual.setArea(rol.getArea());
            return rolRepository.save(actual);
        }

        Rol nuevo = new Rol();
        nuevo.setNombre(rol.getNombre());
        nuevo.setDescripcion(rol.getDescripcion());
        nuevo.setArea(rol.getArea());
        return rolRepository.save(nuevo);
    }

    @Transactional
    public Rol asignarPermisos(Integer idRol, List<Integer> idsPermisos, Usuario operador) {
        autorizacionService.exigirAdministrador(operador);
        Rol rol = obtenerPorId(idRol);
        if (idsPermisos != null && idsPermisos.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Uno o más permisos seleccionados no existen.");
        }

        Set<Permiso> permisos = (idsPermisos == null || idsPermisos.isEmpty())
                ? new HashSet<>()
                : new HashSet<>(permisoRepository.findAllById(idsPermisos));
        if (idsPermisos != null && permisos.size() != new HashSet<>(idsPermisos).size()) {
            throw new IllegalArgumentException("Uno o más permisos seleccionados no existen.");
        }

        rol.setPermisos(permisos);
        return rolRepository.save(rol);
    }
}
