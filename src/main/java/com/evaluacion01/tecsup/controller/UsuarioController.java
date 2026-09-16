package com.evaluacion01.tecsup.controller;

import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.RolRepository;
import com.evaluacion01.tecsup.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.propertyeditors.CustomNumberEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final RolRepository rolRepository;

    @Autowired
    public UsuarioController(UsuarioService usuarioService, RolRepository rolRepository) {
        this.usuarioService = usuarioService;
        this.rolRepository = rolRepository;
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(Long.class, new CustomNumberEditor(Long.class, true));
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("listaUsuarios", usuarioService.listarTodos());
        model.addAttribute("usuarioForm", new Usuario());
        model.addAttribute("roles", rolRepository.findAll());
        return "formulario";
    }

    @GetMapping("/editar/{id}")
    public String mostrarFormularioEditar(@PathVariable Long id, Model model) {
        model.addAttribute("listaUsuarios", usuarioService.listarTodos());
        model.addAttribute("usuarioForm", usuarioService.obtenerPorId(id));
        model.addAttribute("roles", rolRepository.findAll());
        return "formulario";
    }

    @PostMapping("/guardar")
    public String guardar(@ModelAttribute("usuarioForm") Usuario usuario) {
        if (usuario.getIdUsuario() != null) {
            usuarioService.actualizarUsuario(usuario.getIdUsuario(), usuario);
        } else {
            usuarioService.registrarUsuario(usuario);
        }
        return "redirect:/usuarios";
    }

    @GetMapping("/api")
    @ResponseBody
    public List<Usuario> listarApi() {
        return usuarioService.listarTodos();
    }

    @GetMapping("/api/{id}")
    @ResponseBody
    public Usuario obtenerPorIdApi(@PathVariable Long id) {
        return usuarioService.obtenerPorId(id);
    }

    @PostMapping("/api/guardar")
    @ResponseBody
    public Usuario registrarApi(@RequestBody Usuario usuario) {
        return usuarioService.registrarUsuario(usuario);
    }

    @PutMapping("/api/actualizar/{id}")
    @ResponseBody
    public Usuario actualizarApi(@PathVariable Long id, @RequestBody Usuario usuario) {
        return usuarioService.actualizarUsuario(id, usuario);
    }
}