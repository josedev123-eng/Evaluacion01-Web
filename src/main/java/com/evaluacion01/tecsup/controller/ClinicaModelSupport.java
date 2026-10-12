package com.evaluacion01.tecsup.controller;

import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.service.AtencionService;
import com.evaluacion01.tecsup.service.AutorizacionService;
import com.evaluacion01.tecsup.service.PacienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ClinicaModelSupport {

    private final PacienteService pacientes;
    private final AtencionService atenciones;
    private final AutorizacionService autorizacion;

    public void prepararDetalle(Long id, Usuario operador, Model model) {
        model.addAttribute("paciente", pacientes.obtener(operador, id));
        boolean puedeVer = autorizacion.tienePermiso(operador, "atenciones", "VER");
        model.addAttribute("atenciones", puedeVer ? atenciones.consultar(operador, id) : List.of());
        model.addAttribute("puedeVerAtenciones", puedeVer);
        model.addAttribute("puedeRegistrarAtencion", autorizacion.tienePermiso(operador, "atenciones", "CREAR"));
        model.addAttribute("puedeEditarPaciente", autorizacion.tienePermiso(operador, "pacientes", "EDITAR"));
        model.addAttribute("esAdministrador", autorizacion.esAdministrador(operador));
    }
}
