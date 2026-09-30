package com.evaluacion01.tecsup.service;

import com.evaluacion01.tecsup.entity.Rol;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.RolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AutorizacionService {

    private static final String ROL_CON_ACCESO_TOTAL = "Administrador";

    private final RolRepository rolRepository;

    @Transactional(readOnly = true)
    public boolean tienePermiso(Usuario usuario, String modulo, String nombrePermiso) {
        if (usuario == null || usuario.getRol() == null) {
            return false;
        }
        if (ROL_CON_ACCESO_TOTAL.equalsIgnoreCase(usuario.getRol().getNombre())) {
            return true;
        }

        Rol rol = rolRepository.findById(usuario.getRol().getIdRol()).orElse(null);
        if (rol == null) {
            return false;
        }

        return rol.getPermisos().stream().anyMatch(p ->
                p.getModulo().equalsIgnoreCase(modulo) && p.getNombre().equalsIgnoreCase(nombrePermiso));
    }
}
