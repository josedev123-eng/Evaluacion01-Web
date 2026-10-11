package com.evaluacion01.tecsup.controller;

import com.evaluacion01.tecsup.audit.ModuloAuditoria;
import com.evaluacion01.tecsup.dto.AtencionFormulario;
import com.evaluacion01.tecsup.dto.PacienteFormulario;
import com.evaluacion01.tecsup.entity.Paciente;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.service.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/pacientes")
@RequiredArgsConstructor
public class PacienteController {

    private final PacienteService pacientes;
    private final ClinicaModelSupport vista;
    private final AutorizacionService autorizacion;
    private final AuditoriaService auditoria;

    @InitBinder("pacienteForm")
    public void camposPermitidos(WebDataBinder binder) {
        binder.setAllowedFields("idPaciente", "nombres", "apellidos", "dni", "fechaNacimiento", "telefono");
    }

    @GetMapping
    public String listar(@RequestParam(required = false) String q, HttpSession session, Model model) {
        Usuario operador = operador(session);
        model.addAttribute("pacientes", pacientes.buscar(operador, q));
        model.addAttribute("q", q);
        model.addAttribute("puedeCrearPaciente", autorizacion.tienePermiso(operador, "pacientes", "CREAR"));
        model.addAttribute("puedeEditarPaciente", autorizacion.tienePermiso(operador, "pacientes", "EDITAR"));
        return "pacientes/lista";
    }

    @GetMapping("/nuevo")
    public String nuevo(HttpSession session, Model model) {
        exigirGestion(operador(session), false);
        model.addAttribute("pacienteForm", new PacienteFormulario());
        return "pacientes/formulario";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, HttpSession session, Model model) {
        Usuario operador = operador(session);
        exigirGestion(operador, true);
        Paciente paciente = pacientes.obtener(operador, id);
        PacienteFormulario formulario = new PacienteFormulario();
        formulario.setIdPaciente(id);
        formulario.setNombres(paciente.getNombres());
        formulario.setApellidos(paciente.getApellidos());
        formulario.setDni(paciente.getDni());
        formulario.setFechaNacimiento(paciente.getFechaNacimiento());
        formulario.setTelefono(paciente.getTelefono());
        model.addAttribute("pacienteForm", formulario);
        return "pacientes/formulario";
    }

    @PostMapping("/guardar")
    public String guardar(@Valid @ModelAttribute("pacienteForm") PacienteFormulario formulario,
                          BindingResult binding, HttpSession session, Model model,
                          HttpServletResponse response, RedirectAttributes redirect) {
        Usuario operador = operador(session);
        exigirGestion(operador, formulario.getIdPaciente() != null);
        if (!binding.hasErrors()) {
            try {
                Paciente paciente = pacientes.guardar(operador, formulario);
                redirect.addFlashAttribute("exito", "Paciente guardado correctamente.");
                return "redirect:/pacientes/" + paciente.getIdPaciente();
            } catch (IllegalArgumentException e) {
                binding.reject("paciente.invalido", e.getMessage());
            } catch (DataIntegrityViolationException e) {
                binding.reject("paciente.duplicado", "No se pudo guardar el paciente. Revisa si el DNI ya está registrado.");
            }
        } else {
            auditoria.registrarFallo(operador, null, ModuloAuditoria.PACIENTES, "GUARDAR_PACIENTE", "Paciente",
                    formulario.getIdPaciente(), "FORMULARIO_INVALIDO", false);
        }
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return "pacientes/formulario";
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable Long id, HttpSession session, Model model) {
        AtencionFormulario formulario = new AtencionFormulario();
        formulario.setIdPaciente(id);
        model.addAttribute("atencionForm", formulario);
        vista.prepararDetalle(id, operador(session), model);
        return "pacientes/detalle";
    }

    private void exigirGestion(Usuario operador, boolean editar) {
        if (!autorizacion.tienePermiso(operador, "pacientes", "VER")
                || !autorizacion.tienePermiso(operador, "pacientes", editar ? "EDITAR" : "CREAR")) {
            auditoria.registrarFallo(operador, null, ModuloAuditoria.PACIENTES, editar ? "EDITAR_PACIENTE" : "CREAR_PACIENTE",
                    null, null, "SIN_PERMISO", true);
            throw new AccessDeniedException("No tienes permiso para registrar o editar pacientes.");
        }
    }

    private Usuario operador(HttpSession session) {
        return (Usuario) session.getAttribute("usuarioLogueado");
    }
}
