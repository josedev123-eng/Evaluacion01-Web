package com.evaluacion01.tecsup.controller;

import com.evaluacion01.tecsup.audit.ModuloAuditoria;
import com.evaluacion01.tecsup.audit.ResultadoAuditoria;
import com.evaluacion01.tecsup.dto.AuditoriaFiltroDto;
import com.evaluacion01.tecsup.entity.AuditoriaLog;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.service.AuditoriaConsultaService;
import com.evaluacion01.tecsup.service.AuditoriaService;
import com.evaluacion01.tecsup.service.AutorizacionService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/auditoria")
@RequiredArgsConstructor
public class AuditoriaController {

    private final AuditoriaConsultaService consulta;
    private final AutorizacionService autorizacion;
    private final AuditoriaService auditoria;

    @InitBinder("filtro")
    public void configurarFiltros(WebDataBinder binder) {
        binder.setAllowedFields("modulo", "resultado", "usuario", "desde", "hasta", "pagina", "tamano");
    }

    @GetMapping
    public String consultar(@Valid @ModelAttribute("filtro") AuditoriaFiltroDto filtro, BindingResult binding,
                             HttpSession session, Model model, RedirectAttributes redirect,
                             HttpServletResponse response) {
        Usuario operador = (Usuario) session.getAttribute("usuarioLogueado");
        response.setHeader("Cache-Control", "no-store");
        if (operador == null) return "redirect:/login";
        if (!autorizacion.esAdministrador(operador)) {
            auditoria.registrarFallo(operador, null, ModuloAuditoria.AUDITORIA, "CONSULTAR_AUDITORIA",
                    null, null, "SOLO_ADMINISTRADOR", true);
            redirect.addFlashAttribute("error", "Solo un Administrador puede consultar la auditoría.");
            return "redirect:/dashboard";
        }
        model.addAttribute("modulos", ModuloAuditoria.values());
        model.addAttribute("resultados", ResultadoAuditoria.values());
        model.addAttribute("errores", List.of());
        if (binding.hasErrors()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("errores", List.of("Filtros inválidos. Revisa las fechas, módulo, resultado, usuario y paginación."));
            model.addAttribute("paginaAuditoria", Page.<AuditoriaLog>empty());
            return "auditoria/lista";
        }
        try {
            model.addAttribute("paginaAuditoria", consulta.consultar(operador, filtro));
            return "auditoria/lista";
        } catch (AccessDeniedException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/dashboard";
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("errores", List.of(e.getMessage()));
            model.addAttribute("paginaAuditoria", Page.<AuditoriaLog>empty());
            return "auditoria/lista";
        }
    }
}
