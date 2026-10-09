package com.evaluacion01.tecsup.exception;

import com.evaluacion01.tecsup.audit.ModuloAuditoria;
import com.evaluacion01.tecsup.dto.ApiResponseDto;
import com.evaluacion01.tecsup.service.AuditoriaService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice(assignableTypes = {
        com.evaluacion01.tecsup.controller.PasswordRecoveryController.class
})
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final AuditoriaService auditoriaService;

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponseDto> handleBadCredentials(BadCredentialsException ex) {
        return new ResponseEntity<>(
                new ApiResponseDto(false, "Usuario o contraseña incorrectos"),
                HttpStatus.UNAUTHORIZED
        );
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiResponseDto> handleDisabledUser(DisabledException ex) {
        return new ResponseEntity<>(
                new ApiResponseDto(false, "La cuenta de usuario está desactivada"),
                HttpStatus.FORBIDDEN
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex,
                                                                         HttpServletRequest request) {
        String accion = request.getRequestURI().endsWith("/forgot-password")
                ? "SOLICITAR_RECUPERACION" : "RESTABLECER_CONTRASENA";
        auditoriaService.registrarFallo(null, null, ModuloAuditoria.AUTENTICACION, accion,
                "Usuario", null, "DATOS_INVALIDOS", false);
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponseDto> handleRuntimeException(RuntimeException ex) {
        return new ResponseEntity<>(
                new ApiResponseDto(false, ex.getMessage()),
                HttpStatus.BAD_REQUEST
        );
    }
}
