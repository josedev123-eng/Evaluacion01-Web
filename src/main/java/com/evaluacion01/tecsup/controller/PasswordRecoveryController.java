package com.evaluacion01.tecsup.controller;

import com.evaluacion01.tecsup.dto.ApiResponseDto;
import com.evaluacion01.tecsup.dto.ForgotPasswordDto;
import com.evaluacion01.tecsup.dto.ResetPasswordDto;
import com.evaluacion01.tecsup.service.PasswordRecoveryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class PasswordRecoveryController {

    @Autowired
    private PasswordRecoveryService passwordRecoveryService;

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponseDto> forgotPassword(@Valid @RequestBody ForgotPasswordDto request) {
        passwordRecoveryService.processForgotPassword(request.getEmail());
        return ResponseEntity.ok(new ApiResponseDto(true, "Token de recuperación enviado al correo"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponseDto> resetPassword(@Valid @RequestBody ResetPasswordDto request) {
        passwordRecoveryService.processResetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(new ApiResponseDto(true, "Contraseña actualizada correctamente"));
    }
}