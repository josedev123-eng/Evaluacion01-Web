package com.evaluacion01.tecsup.controller;

import com.evaluacion01.tecsup.dto.CitaFormulario;
import com.evaluacion01.tecsup.entity.EstadoCita;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.service.AutorizacionService;
import com.evaluacion01.tecsup.service.CitaService;
import com.evaluacion01.tecsup.service.PacienteService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/citas")
@RequiredArgsConstructor
public class CitaController {

    private final CitaService citas;
    private final PacienteService pacientes;
    private final AutorizacionService autorizacion;

    @InitBinder("citaForm")
    public void camposPermitidos(WebDataBinder binder) {
        // El registrador, el estado y el ID de la cita los decide el servidor.
        binder.setAllowedFields("idPaciente", "idMedico", "fechaHora", "motivo");
    }

    @GetMapping
    public String listar(HttpSession session, Model model) {
        Usuario operador = (Usuario) session.getAttribute("usuarioLogueado");
        prepararLista(operador, model, new CitaFormulario());
        return "citas/lista";
    }

    @PostMapping("/guardar")
    public String registrar(@Valid @ModelAttribute("citaForm") CitaFormulario formulario, BindingResult binding,
                            HttpSession session, Model model, HttpServletResponse response,
                            RedirectAttributes redirect) {
        Usuario operador = (Usuario) session.getAttribute("usuarioLogueado");
        if (!binding.hasErrors()) {
            try {
                citas.registrar(operador, formulario);
                redirect.addFlashAttribute("exito", "Cita programada correctamente.");
                return "redirect:/citas";
            } catch (IllegalArgumentException e) {
                binding.reject("cita.invalida", e.getMessage());
            }
        }
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        prepararLista(operador, model, formulario);
        return "citas/lista";
    }

    @PostMapping("/{id}/estado")
    public String cambiarEstado(@PathVariable("id") Long id, @RequestParam("estado") EstadoCita estado,
                                HttpSession session, RedirectAttributes redirect) {
        Usuario operador = (Usuario) session.getAttribute("usuarioLogueado");
        try {
            citas.cambiarEstado(operador, id, estado);
            redirect.addFlashAttribute("exito", "Cita " + estado.getEtiqueta().toLowerCase() + " correctamente.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/citas";
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable("id") Long id, HttpSession session, RedirectAttributes redirect) {
        Usuario operador = (Usuario) session.getAttribute("usuarioLogueado");
        try {
            citas.eliminar(operador, id);
            redirect.addFlashAttribute("exito", "Cita eliminada correctamente.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/citas";
    }

    private void prepararLista(Usuario operador, Model model, CitaFormulario formulario) {
        boolean puedeCrear = autorizacion.tienePermiso(operador, "citas", "CREAR")
                && autorizacion.tienePermiso(operador, "pacientes", "VER");
        model.addAttribute("citas", citas.consultar(operador));
        model.addAttribute("citaForm", formulario);
        model.addAttribute("puedeCrearCita", puedeCrear);
        model.addAttribute("puedeEditarCita", autorizacion.tienePermiso(operador, "citas", "EDITAR"));
        model.addAttribute("veTodasLasCitas", citas.veTodas(operador));
        model.addAttribute("pacientesCita", puedeCrear ? pacientes.buscar(operador, null) : List.of());
        model.addAttribute("medicosCita", puedeCrear ? citas.medicosDisponibles(operador) : List.of());
    }
}
