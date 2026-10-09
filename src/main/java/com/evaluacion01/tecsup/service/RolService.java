package com.evaluacion01.tecsup.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final AuditoriaService auditoriaService;

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
    public Rol guardar(Rol rol) {
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
            Map<String, String> antes = resumen(actual);
            actual.setNombre(rol.getNombre());
            actual.setDescripcion(rol.getDescripcion());
            actual.setArea(rol.getArea());
            Rol guardado = rolRepository.save(actual);
            // RF-AUD-01: modificación de rol.
            auditoriaService.registrar(AuditoriaService.MODULO_ROLES, "EDITAR_ROL", "Rol",
                    idLargo(guardado.getIdRol()), diferenciar(antes, resumen(guardado)));
            return guardado;
        }

        Rol guardado = rolRepository.save(rol);
        // RF-AUD-01: alta de rol.
        auditoriaService.registrar(AuditoriaService.MODULO_ROLES, "CREAR_ROL", "Rol",
                idLargo(guardado.getIdRol()),
                "Rol creado: " + guardado.getNombre() + " (área " + guardado.getArea() + ")");
        return guardado;
    }

    @Transactional
    public Rol asignarPermisos(Integer idRol, List<Integer> idsPermisos) {
        Rol rol = obtenerPorId(idRol);

        Set<Permiso> anteriores = new HashSet<>(rol.getPermisos());

        Set<Permiso> permisos = (idsPermisos == null || idsPermisos.isEmpty())
                ? new HashSet<>()
                : new HashSet<>(permisoRepository.findAllById(idsPermisos));

        rol.setPermisos(permisos);
        Rol guardado = rolRepository.save(rol);

        // RF-AUD-01: alta y baja de permisos sobre un rol.
        auditoriaService.registrar(AuditoriaService.MODULO_PERMISOS, "CAMBIAR_PERMISOS_ROL", "Rol",
                idLargo(idRol), detalleCambiosDePermisos(anteriores, permisos));

        return guardado;
    }

    private static Long idLargo(Integer id) {
        return id == null ? null : id.longValue();
    }

    private Map<String, String> resumen(Rol rol) {
        Map<String, String> resumen = new LinkedHashMap<>();
        resumen.put("nombre", rol.getNombre());
        resumen.put("descripcion", rol.getDescripcion());
        resumen.put("area", rol.getArea());
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

    private String detalleCambiosDePermisos(Set<Permiso> anteriores, Set<Permiso> actuales) {
        List<String> agregados = actuales.stream()
                .filter(permiso -> anteriores.stream().noneMatch(anterior -> Objects.equals(anterior.getIdPermiso(), permiso.getIdPermiso())))
                .map(RolService::nombreCompleto)
                .sorted()
                .toList();

        List<String> quitados = anteriores.stream()
                .filter(permiso -> actuales.stream().noneMatch(actual -> Objects.equals(actual.getIdPermiso(), permiso.getIdPermiso())))
                .map(RolService::nombreCompleto)
                .sorted()
                .toList();

        if (agregados.isEmpty() && quitados.isEmpty()) {
            return "Sin cambios en los permisos (" + actuales.size() + " asignados)";
        }
        StringBuilder detalle = new StringBuilder();
        if (!agregados.isEmpty()) {
            detalle.append("Permisos agregados: ").append(String.join(", ", agregados));
        }
        if (!quitados.isEmpty()) {
            if (detalle.length() > 0) {
                detalle.append("; ");
            }
            detalle.append("Permisos quitados: ").append(String.join(", ", quitados));
        }
        return detalle.toString();
    }

    private static String nombreCompleto(Permiso permiso) {
        return permiso.getModulo() + "." + permiso.getNombre();
    }
}
