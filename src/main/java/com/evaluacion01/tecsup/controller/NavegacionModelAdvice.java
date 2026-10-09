package com.evaluacion01.tecsup.controller;

import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.service.AutorizacionService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

// Agrega a todas las vistas si el usuario logueado puede ver Usuarios/Roles/Auditoría,
// para poder ocultar esos enlaces del menú cuando su rol no tiene permiso.
@ControllerAdvice(annotations = org.springframework.stereotype.Controller.class)
@RequiredArgsConstructor
public class NavegacionModelAdvice {

    private final AutorizacionService autorizacionService;

    @ModelAttribute
    public void agregarPermisosDeNavegacion(HttpSession session, Model model) {
        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        model.addAttribute("puedeVerUsuarios", autorizacionService.tienePermiso(usuario, "usuarios", "VER"));
        model.addAttribute("puedeVerRoles", autorizacionService.tienePermiso(usuario, "roles", "VER"));
        model.addAttribute("puedeVerAuditoria", autorizacionService.tienePermiso(usuario, "auditoria", "VER"));
    }
}
