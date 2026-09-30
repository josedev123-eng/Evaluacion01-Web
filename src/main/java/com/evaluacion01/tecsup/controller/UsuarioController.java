package com.evaluacion01.tecsup.controller;

import com.evaluacion01.tecsup.entity.Rol;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.service.AutorizacionService;
import com.evaluacion01.tecsup.service.RolService;
import com.evaluacion01.tecsup.service.UsuarioService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/usuarios")
public class UsuarioController {

    private static final String MODULO = "usuarios";

    private final UsuarioService usuarioService;
    private final RolService rolService;
    private final AutorizacionService autorizacionService;

    @Autowired
    public UsuarioController(UsuarioService usuarioService, RolService rolService, AutorizacionService autorizacionService) {
        this.usuarioService = usuarioService;
        this.rolService = rolService;
        this.autorizacionService = autorizacionService;
    }

    // Listar usuarios y preparar el objeto para el formulario
    @GetMapping
    public String listar(Model model, HttpSession session) {
        Usuario usuarioLogueado = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuarioLogueado == null) {
            return "redirect:/login";
        }
        if (!autorizacionService.tienePermiso(usuarioLogueado, MODULO, "VER")) {
            return "redirect:/dashboard";
        }
        Usuario nuevoUsuario = new Usuario();
        nuevoUsuario.setRol(new Rol());
        model.addAttribute("listaUsuarios", usuarioService.listarTodos());
        model.addAttribute("usuarioForm", nuevoUsuario);
        model.addAttribute("roles", rolService.listar());
        model.addAttribute("puedeCrear", autorizacionService.tienePermiso(usuarioLogueado, MODULO, "CREAR"));
        model.addAttribute("puedeEditar", autorizacionService.tienePermiso(usuarioLogueado, MODULO, "EDITAR"));
        return "formulario";
    }

    // Cargar formulario con datos de un usuario para editar
    @GetMapping("/editar/{id}")
    public String mostrarFormularioEditar(@PathVariable Long id, Model model, HttpSession session) {
        Usuario usuarioLogueado = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuarioLogueado == null) {
            return "redirect:/login";
        }
        if (!autorizacionService.tienePermiso(usuarioLogueado, MODULO, "EDITAR")) {
            return "redirect:/usuarios";
        }
        model.addAttribute("listaUsuarios", usuarioService.listarTodos());
        model.addAttribute("usuarioForm", usuarioService.obtenerPorId(id));
        model.addAttribute("roles", rolService.listar());
        model.addAttribute("puedeCrear", autorizacionService.tienePermiso(usuarioLogueado, MODULO, "CREAR"));
        model.addAttribute("puedeEditar", true);
        return "formulario";
    }

    // RF-USR-01 / RF-USR-02: Registrar o actualizar usuario mediante formulario HTML.
    // El atributo se llama "usuarioForm" (no "usuario") a propósito: si coincidiera con
    // el nombre del campo `usuario` (username) del propio formulario, Spring intenta
    // convertir ese valor de texto al objeto Usuario completo y revienta con un 400.
    @PostMapping("/guardar")
    public String guardar(@ModelAttribute("usuarioForm") Usuario usuario, BindingResult bindingResult,
                           HttpSession session, RedirectAttributes redirectAttributes) {
        Usuario usuarioLogueado = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuarioLogueado == null) {
            return "redirect:/login";
        }
        boolean esNuevo = usuario.getIdUsuario() == null;
        String permisoRequerido = esNuevo ? "CREAR" : "EDITAR";
        if (!autorizacionService.tienePermiso(usuarioLogueado, MODULO, permisoRequerido)) {
            redirectAttributes.addFlashAttribute("error", "Tu rol no tiene permiso para " + (esNuevo ? "crear" : "editar") + " usuarios.");
            return "redirect:/usuarios";
        }
        if (bindingResult.hasErrors() || usuario.getArea() == null || usuario.getArea().isBlank()
                || usuario.getRol() == null || usuario.getRol().getIdRol() == null) {
            redirectAttributes.addFlashAttribute("error", "Datos del formulario inválidos. Verifica los campos obligatorios, el área y el rol seleccionado.");
            return "redirect:/usuarios";
        }
        if (!rolService.perteneceAArea(usuario.getRol().getIdRol(), usuario.getArea())) {
            redirectAttributes.addFlashAttribute("error", "El rol seleccionado no corresponde al área elegida.");
            return "redirect:/usuarios";
        }
        try {
            if (!esNuevo) {
                usuarioService.actualizarUsuario(usuario.getIdUsuario(), usuario);
            } else {
                usuarioService.registrarUsuario(usuario);
            }
        } catch (DataIntegrityViolationException e) {
            redirectAttributes.addFlashAttribute("error", mensajeDuplicado(e));
        }
        return "redirect:/usuarios";
    }

    // Activar/desactivar usuario (ej. cuando alguien deja de trabajar en el hospital)
    @PostMapping("/{id}/estado")
    public String cambiarEstado(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        Usuario usuarioLogueado = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuarioLogueado == null) {
            return "redirect:/login";
        }
        if (!autorizacionService.tienePermiso(usuarioLogueado, MODULO, "EDITAR")) {
            redirectAttributes.addFlashAttribute("error", "Tu rol no tiene permiso para activar/desactivar usuarios.");
            return "redirect:/usuarios";
        }
        Usuario usuario = usuarioService.cambiarEstado(id);
        String estado = Boolean.TRUE.equals(usuario.getEstado()) ? "activado" : "desactivado";
        redirectAttributes.addFlashAttribute("exito", "Usuario " + estado + " correctamente");
        return "redirect:/usuarios";
    }

    private String mensajeDuplicado(DataIntegrityViolationException e) {
        String mensaje = e.getMostSpecificCause().getMessage();
        if (mensaje.contains("'usuario'") || mensaje.contains("for key 'usuario'")) {
            return "Ya existe un usuario con ese nombre de usuario.";
        }
        if (mensaje.contains("'correo'") || mensaje.contains("for key 'correo'")) {
            return "Ya existe un usuario con ese correo.";
        }
        if (mensaje.contains("'dni'") || mensaje.contains("for key 'dni'")) {
            return "Ya existe un usuario con ese DNI.";
        }
        return "No se pudo guardar: algunos datos ya están registrados con otro usuario.";
    }
}
