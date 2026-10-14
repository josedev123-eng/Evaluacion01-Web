package com.evaluacion01.tecsup.controller;

import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.service.AutorizacionService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

// Navegación compartida: ningún enlace concede acceso por sí mismo.
@ControllerAdvice(annotations = org.springframework.stereotype.Controller.class)
@RequiredArgsConstructor
public class NavegacionModelAdvice {

    private final AutorizacionService autorizacionService;

    @ModelAttribute
    public void agregarPermisosDeNavegacion(HttpSession session, Model model) {
        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        boolean puedeVerPacientes = autorizacionService.tienePermiso(usuario, "pacientes", "VER");
        model.addAttribute("puedeVerPacientes", puedeVerPacientes);
        model.addAttribute("puedeVerAtenciones", puedeVerPacientes
                && autorizacionService.tienePermiso(usuario, "atenciones", "VER"));
        model.addAttribute("puedeVerCitas", autorizacionService.tienePermiso(usuario, "citas", "VER"));
        model.addAttribute("puedeVerUsuarios",autorizacionService.tienePermiso(usuario, "usuarios", "VER"));
        model.addAttribute("puedeVerRoles", autorizacionService.tienePermiso(usuario, "roles", "VER"));
        model.addAttribute("puedeVerAuditoria", autorizacionService.esAdministrador(usuario));
    }
}
