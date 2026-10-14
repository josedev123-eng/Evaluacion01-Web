package com.evaluacion01.tecsup.controller;

import com.evaluacion01.tecsup.dto.PersonaReniec;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.exception.ReniecNoDisponibleException;
import com.evaluacion01.tecsup.service.AutorizacionService;
import com.evaluacion01.tecsup.service.ReniecService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

// Autocompleta nombres y apellidos desde el campo DNI de los formularios de usuarios y pacientes.
@RestController
@RequestMapping("/api/reniec")
@RequiredArgsConstructor
public class ReniecController {

    private final ReniecService reniec;
    private final AutorizacionService autorizacion;

    @GetMapping("/dni/{dni}")
    public ResponseEntity<Map<String, Object>> consultar(@PathVariable String dni, HttpSession session) {
        Usuario operador = (Usuario) session.getAttribute("usuarioLogueado");
        if (operador == null) {
            return respuesta(HttpStatus.UNAUTHORIZED, "Inicia sesión para consultar.");
        }
        if (!puedeGestionarPersonas(operador)) {
            return respuesta(HttpStatus.FORBIDDEN, "Tu rol no tiene permiso para esta consulta.");
        }
        if (!dni.matches("\\d{8}")) {
            return respuesta(HttpStatus.BAD_REQUEST, "El DNI debe tener 8 dígitos.");
        }
        if (!reniec.habilitado()) {
            return respuesta(HttpStatus.SERVICE_UNAVAILABLE, "La consulta a RENIEC no está configurada.");
        }
        try {
            Optional<PersonaReniec> persona = reniec.consultar(dni);
            if (persona.isEmpty()) {
                return respuesta(HttpStatus.NOT_FOUND, "No se encontró ese DNI en RENIEC.");
            }
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(Map.of("nombres", persona.get().nombres(), "apellidos", persona.get().apellidos(),
                            "simulado", persona.get().simulado()));
        } catch (ReniecNoDisponibleException e) {
            return respuesta(HttpStatus.BAD_GATEWAY, "No se pudo consultar RENIEC. Completa los datos a mano.");
        }
    }

    private boolean puedeGestionarPersonas(Usuario operador) {
        return autorizacion.tienePermiso(operador, "usuarios", "CREAR")
                || autorizacion.tienePermiso(operador, "usuarios", "EDITAR")
                || autorizacion.tienePermiso(operador, "pacientes", "CREAR")
                || autorizacion.tienePermiso(operador, "pacientes", "EDITAR");
    }

    private ResponseEntity<Map<String, Object>> respuesta(HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado).cacheControl(CacheControl.noStore()).body(Map.of("mensaje", mensaje));
    }
}
