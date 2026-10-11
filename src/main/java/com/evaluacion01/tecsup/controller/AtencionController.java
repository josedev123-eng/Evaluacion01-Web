package com.evaluacion01.tecsup.controller;

import com.evaluacion01.tecsup.audit.ModuloAuditoria;
import com.evaluacion01.tecsup.dto.AtencionFormulario;
import com.evaluacion01.tecsup.entity.Atencion;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.service.AtencionService;
import com.evaluacion01.tecsup.service.AuditoriaService;
import com.evaluacion01.tecsup.service.AutorizacionService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/atenciones")
@RequiredArgsConstructor
public class AtencionController {

    private final AtencionService atenciones;
    private final AutorizacionService autorizacion;
    private final AuditoriaService auditoria;
    private final ClinicaModelSupport vista;

    @InitBinder("atencionForm")
    public void camposPermitidos(WebDataBinder binder) {
        // No admitir profesional, fecha, ID de atención ni asociaciones del formulario.
        binder.setAllowedFields("idPaciente", "motivo", "diagnostico", "indicaciones");
    }

    @GetMapping
    public String listar(HttpSession session, Model model) {
        Usuario operador = (Usuario) session.getAttribute("usuarioLogueado");
        model.addAttribute("atenciones", atenciones.consultar(operador, null));
        model.addAttribute("esAdministrador", autorizacion.esAdministrador(operador));
        return "atenciones/lista";
    }

    @PostMapping("/guardar")
    public String registrar(@Valid @ModelAttribute("atencionForm") AtencionFormulario formulario,
                            BindingResult binding, HttpSession session, Model model,
                            HttpServletResponse response, RedirectAttributes redirect) {
        Usuario operador = (Usuario) session.getAttribute("usuarioLogueado");
        if (!autorizacion.tienePermiso(operador, "pacientes", "VER")
                || !autorizacion.tienePermiso(operador, "atenciones", "CREAR")) {
            auditoria.registrarFallo(operador, null, ModuloAuditoria.ATENCIONES, "REGISTRAR_ATENCION", "Paciente",
                    formulario.getIdPaciente(), "SIN_PERMISO", true);
            throw new AccessDeniedException("No tienes permiso para registrar atenciones.");
        }
        if (!binding.hasErrors()) {
            try {
                Atencion atencion = atenciones.registrar(operador, formulario);
                redirect.addFlashAttribute("exito", "Atención #" + atencion.getIdAtencion() + " registrada correctamente.");
                return "redirect:/pacientes/" + atencion.getPaciente().getIdPaciente();
            } catch (IllegalArgumentException e) {
                binding.reject("atencion.invalida", e.getMessage());
            }
        } else {
            auditoria.registrarFallo(operador, null, ModuloAuditoria.ATENCIONES, "REGISTRAR_ATENCION", "Paciente",
                    formulario.getIdPaciente(), "FORMULARIO_INVALIDO", false);
        }
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        if (formulario.getIdPaciente() == null || formulario.getIdPaciente() <= 0) {
            model.addAttribute("status", 400);
            model.addAttribute("error", "Datos inválidos");
            model.addAttribute("message", "Selecciona un paciente válido.");
            return "error";
        }
        vista.prepararDetalle(formulario.getIdPaciente(), operador, model);
        return "pacientes/detalle";
    }
}
