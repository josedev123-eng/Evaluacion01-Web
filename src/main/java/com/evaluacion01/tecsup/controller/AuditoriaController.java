package com.evaluacion01.tecsup.controller;

import java.util.List;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.evaluacion01.tecsup.entity.AuditoriaLog;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.service.AutorizacionService;
import com.evaluacion01.tecsup.service.AuditoriaService;

import lombok.RequiredArgsConstructor;

// RF-AUD-01: consulta del registro de operaciones críticas.
@Controller
@RequestMapping("/auditoria")
@RequiredArgsConstructor
public class AuditoriaController {

    private static final String MODULO = "auditoria";

    private final AuditoriaService auditoriaService;
    private final AutorizacionService autorizacionService;

    @GetMapping
    public String listar(@RequestParam(name = "modulo", required = false) String modulo,
                         @RequestParam(name = "usuario", required = false) String usuario,
                         Model model, HttpSession session) {
        Usuario usuarioLogueado = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuarioLogueado == null) {
            return "redirect:/login";
        }
        if (!autorizacionService.tienePermiso(usuarioLogueado, MODULO, "VER")) {
            return "redirect:/dashboard";
        }

        String filtroModulo = (modulo == null || modulo.isBlank()) ? null : modulo.trim();
        String filtroUsuario = (usuario == null || usuario.isBlank()) ? null : usuario.trim();

        List<AuditoriaLog> registros = auditoriaService.listarFiltrado(filtroModulo, filtroUsuario);

        model.addAttribute("registros", registros);
        model.addAttribute("moduloFiltro", filtroModulo == null ? "" : filtroModulo);
        model.addAttribute("usuarioFiltro", filtroUsuario == null ? "" : filtroUsuario);
        model.addAttribute("modulos", List.of(
                AuditoriaService.MODULO_AUTENTICACION,
                AuditoriaService.MODULO_USUARIOS,
                AuditoriaService.MODULO_ROLES,
                AuditoriaService.MODULO_PERMISOS));
        model.addAttribute("totalRegistros", registros.size());
        return "auditoria/lista-auditoria";
    }
}
