package com.evaluacion01.tecsup.exception;

import com.evaluacion01.tecsup.controller.AtencionController;
import com.evaluacion01.tecsup.controller.CitaController;
import com.evaluacion01.tecsup.controller.PacienteController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@ControllerAdvice(assignableTypes = {PacienteController.class, AtencionController.class, CitaController.class})
public class ClinicaExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public String denegado(AccessDeniedException e, Model model, HttpServletResponse response) {
        return error(403, "Acceso denegado", e.getMessage(), model, response);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public String noEncontrado(ResponseStatusException e, Model model, HttpServletRequest request, HttpServletResponse response) {
        model.addAttribute("path", request.getRequestURI());
        return error(e.getStatusCode().value(), "Solicitud no completada", e.getReason(), model, response);
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    public String invalido(Exception e, Model model, HttpServletResponse response) {
        return error(400, "Datos inválidos", "Revisa los datos y el identificador solicitado.", model, response);
    }

    @ExceptionHandler(Exception.class)
    public String inesperado(Exception e, Model model, HttpServletResponse response) {
        // Nunca mostrar SQL, parámetros clínicos o trazas al usuario.
        return error(500, "Operación no completada", "No se pudo completar la operación. Intenta nuevamente.", model, response);
    }

    private String error(int status, String titulo, String mensaje, Model model, HttpServletResponse response) {
        response.setStatus(status);
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("status", status);
        model.addAttribute("error", titulo);
        model.addAttribute("message", mensaje);
        return "error";
    }
}
