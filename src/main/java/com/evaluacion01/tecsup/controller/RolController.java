package com.evaluacion01.tecsup.controller;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.evaluacion01.tecsup.entity.Permiso;
import com.evaluacion01.tecsup.entity.Rol;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.service.AutorizacionService;
import com.evaluacion01.tecsup.service.RolService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RolController {

    private static final String MODULO = "roles";

    private final RolService rolService;
    private final AutorizacionService autorizacionService;

    @InitBinder("rol")
    public void configurarFormulario(WebDataBinder binder) {
        binder.setAllowedFields("idRol", "nombre", "descripcion", "area");
    }

    @GetMapping
    public String listar(Model model, HttpSession session) {
        Usuario usuarioLogueado = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuarioLogueado == null) {
            return "redirect:/login";
        }
        if (!autorizacionService.tienePermiso(usuarioLogueado, MODULO, "VER")) {
            return "redirect:/dashboard";
        }
        model.addAttribute("roles", rolService.listar());
        model.addAttribute("puedeEditar", autorizacionService.tienePermiso(usuarioLogueado, MODULO, "EDITAR"));
        return "roles/lista-roles";
    }

    @GetMapping("/nuevo")
    public String nuevoForm(Model model, HttpSession session) {
        if (!tienePermisoEditar(session)) {
            return "redirect:/roles";
        }
        model.addAttribute("rol", new Rol());
        return "roles/form-rol";
    }

    @GetMapping("/{id}/editar")
    public String editarForm(@PathVariable("id") Integer id, Model model, HttpSession session) {
        if (!tienePermisoEditar(session)) {
            return "redirect:/roles";
        }
        model.addAttribute("rol", rolService.obtenerPorId(id));
        return "roles/form-rol";
    }

    @PostMapping("/guardar")
    public String guardar(@ModelAttribute("rol") Rol rol, BindingResult bindingResult, HttpSession session,
                           RedirectAttributes redirectAttributes) {
        if (!tienePermisoEditar(session)) {
            redirectAttributes.addFlashAttribute("error", "Tu rol no tiene permiso para crear/editar roles.");
            return "redirect:/roles";
        }
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Datos del formulario inválidos. Verifica el nombre del rol.");
            return "redirect:/roles";
        }
        try {
            rolService.guardar(rol, (Usuario) session.getAttribute("usuarioLogueado"));
            redirectAttributes.addFlashAttribute("exito", "Rol guardado correctamente");
        } catch (IllegalArgumentException | AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/roles";
    }

    @GetMapping("/{id}/permisos")
    public String asignarPermisosForm(@PathVariable("id") Integer id, Model model, HttpSession session) {
        if (!tienePermisoEditar(session)) {
            return "redirect:/roles";
        }
        Rol rol = rolService.obtenerPorId(id);
        Set<Integer> idsAsignados = rol.getPermisos().stream()
                .map(Permiso::getIdPermiso)
                .collect(Collectors.toSet());

        model.addAttribute("rol", rol);
        model.addAttribute("permisos", rolService.listarPermisos());
        model.addAttribute("idsAsignados", idsAsignados);
        return "roles/asignar-permisos";
    }

    @PostMapping("/{id}/permisos")
    public String asignarPermisos(@PathVariable("id") Integer id,
                                   @RequestParam(name = "permisoIds", required = false) List<Integer> permisoIds,
                                   HttpSession session, RedirectAttributes redirectAttributes) {
        if (!tienePermisoEditar(session)) {
            redirectAttributes.addFlashAttribute("error", "Tu rol no tiene permiso para asignar permisos.");
            return "redirect:/roles";
        }
        try {
            rolService.asignarPermisos(id, permisoIds, (Usuario) session.getAttribute("usuarioLogueado"));
            redirectAttributes.addFlashAttribute("exito", "Permisos actualizados correctamente");
        } catch (IllegalArgumentException | AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/roles";
    }

    private boolean tienePermisoEditar(HttpSession session) {
        Usuario usuarioLogueado = (Usuario) session.getAttribute("usuarioLogueado");
        return usuarioLogueado != null && autorizacionService.tienePermiso(usuarioLogueado, MODULO, "EDITAR");
    }
}
