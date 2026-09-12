package com.evaluacion01.tecsup.controller;

import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    @Autowired
    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    // Listar usuarios y preparar el objeto para el formulario
    @GetMapping
    public String listar(Model model) {
        model.addAttribute("listaUsuarios", usuarioService.listarTodos());
        model.addAttribute("usuario", new Usuario());
        return "formulario";
    }

    // Cargar formulario con datos de un usuario para editar
    @GetMapping("/editar/{id}")
    public String mostrarFormularioEditar(@PathVariable Long id, Model model) {
        model.addAttribute("listaUsuarios", usuarioService.listarTodos());
        model.addAttribute("usuario", usuarioService.obtenerPorId(id));
        return "formulario";
    }

    // RF-USR-01: Registrar o actualizar usuario mediante formulario HTML
    @PostMapping("/guardar")
    public String guardar(@ModelAttribute("usuario") Usuario usuario) {
        if (usuario.getIdUsuario() != null) {
            // RF-USR-02: Editar
            usuarioService.actualizarUsuario(usuario.getIdUsuario(), usuario);
        } else {
            // RF-USR-01: Crear
            usuarioService.registrarUsuario(usuario);
        }
        return "redirect:/usuarios";
    }
}