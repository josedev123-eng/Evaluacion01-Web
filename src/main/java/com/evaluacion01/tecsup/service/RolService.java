package com.evaluacion01.tecsup.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.evaluacion01.tecsup.entity.Permiso;
import com.evaluacion01.tecsup.entity.Rol;
import com.evaluacion01.tecsup.repository.PermisoRepository;
import com.evaluacion01.tecsup.repository.RolRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RolService {

    private final RolRepository rolRepository;
    private final PermisoRepository permisoRepository;

    public List<Rol> listar() {
        return rolRepository.findAll();
    }

    public Rol obtenerPorId(Integer idRol) {
        return rolRepository.findById(idRol)
                .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado: " + idRol));
    }

    public List<Permiso> listarPermisos() {
        return permisoRepository.findAll();
    }

    @Transactional
    public Rol guardar(Rol rol) {
        if (rol.getNombre() == null || rol.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del rol es obligatorio");
        }
        rol.setNombre(rol.getNombre().trim());

        rolRepository.findByNombreIgnoreCase(rol.getNombre()).ifPresent(existente -> {
            if (!existente.getIdRol().equals(rol.getIdRol())) {
                throw new IllegalArgumentException("Ya existe un rol con el nombre '" + rol.getNombre() + "'");
            }
        });

        if (rol.getIdRol() != null) {
            Rol actual = obtenerPorId(rol.getIdRol());
            actual.setNombre(rol.getNombre());
            actual.setDescripcion(rol.getDescripcion());
            return rolRepository.save(actual);
        }

        return rolRepository.save(rol);
    }

    @Transactional
    public Rol asignarPermisos(Integer idRol, List<Integer> idsPermisos) {
        Rol rol = obtenerPorId(idRol);

        Set<Permiso> permisos = (idsPermisos == null || idsPermisos.isEmpty())
                ? new HashSet<>()
                : new HashSet<>(permisoRepository.findAllById(idsPermisos));

        rol.setPermisos(permisos);
        return rolRepository.save(rol);
    }
}
