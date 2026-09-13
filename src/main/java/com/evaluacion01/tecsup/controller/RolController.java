package com.evaluacion01.tecsup.controller;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.evaluacion01.tecsup.entity.Permiso;
import com.evaluacion01.tecsup.entity.Rol;
import com.evaluacion01.tecsup.service.RolService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RolController {

    private final RolService rolService;

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("roles", rolService.listar());
        return "roles/lista-roles";
    }

    @GetMapping("/nuevo")
    public String nuevoForm(Model model) {
        model.addAttribute("rol", new Rol());
        return "roles/form-rol";
    }

    @GetMapping("/{id}/editar")
    public String editarForm(@PathVariable("id") Integer id, Model model) {
        model.addAttribute("rol", rolService.obtenerPorId(id));
        return "roles/form-rol";
    }

    @PostMapping("/guardar")
    public String guardar(@ModelAttribute Rol rol, RedirectAttributes redirectAttributes) {
        try {
            rolService.guardar(rol);
            redirectAttributes.addFlashAttribute("exito", "Rol guardado correctamente");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/roles";
    }

    @GetMapping("/{id}/permisos")
    public String asignarPermisosForm(@PathVariable("id") Integer id, Model model) {
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
                                   RedirectAttributes redirectAttributes) {
        rolService.asignarPermisos(id, permisoIds);
        redirectAttributes.addFlashAttribute("exito", "Permisos actualizados correctamente");
        return "redirect:/roles";
    }
}
