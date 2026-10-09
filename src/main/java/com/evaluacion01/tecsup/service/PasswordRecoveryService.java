package com.evaluacion01.tecsup.service;

import com.evaluacion01.tecsup.audit.ModuloAuditoria;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PasswordRecoveryService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuditoriaService auditoriaService;

    @Transactional
    public void processForgotPassword(String email) {
        Usuario destino = null;
        String motivo = "DATOS_INCOMPLETOS";
        try {
            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("El correo electrónico es obligatorio");
            }
            motivo = "CORREO_NO_REGISTRADO";
            destino = usuarioRepository.findByCorreo(email.trim())
                    .orElseThrow(() -> new IllegalArgumentException("No existe un usuario con el correo ingresado"));
            motivo = "ERROR_SOLICITUD_RECUPERACION";
            String token = UUID.randomUUID().toString();
            destino.setResetToken(token);
            destino.setResetTokenExpiry(LocalDateTime.now().plusMinutes(15));
            usuarioRepository.saveAndFlush(destino);
            motivo = "ERROR_ENTREGA_RECUPERACION";
            sendEmail(destino.getCorreo(), token);
            auditoriaService.registrarExito(null, ModuloAuditoria.AUTENTICACION, "SOLICITAR_RECUPERACION",
                    "Usuario", destino.getIdUsuario(), "Token de recuperación generado");
        } catch (RuntimeException e) {
            registrarFallo("SOLICITAR_RECUPERACION", destino, motivo);
            throw e;
        }
    }

    @Transactional
    public void processResetPassword(String token, String newPassword) {
        Usuario destino = null;
        String motivo = "RECUPERACION_INVALIDA";
        try {
            if (token == null || token.isBlank()) {
                throw new IllegalArgumentException("El token de recuperación es inválido");
            }
            destino = usuarioRepository.findByResetToken(token)
                    .orElseThrow(() -> new IllegalArgumentException("El token de recuperación es inválido"));
            motivo = "RECUPERACION_EXPIRADA";
            if (destino.getResetTokenExpiry() == null || !destino.getResetTokenExpiry().isAfter(LocalDateTime.now())) {
                throw new IllegalArgumentException("El token de recuperación ha expirado");
            }
            motivo = "ERROR_RESTABLECIMIENTO";
            destino.setContrasena(passwordEncoder.encode(newPassword));
            destino.setResetToken(null);
            destino.setResetTokenExpiry(null);
            usuarioRepository.saveAndFlush(destino);
            auditoriaService.registrarExito(null, ModuloAuditoria.AUTENTICACION, "RESTABLECER_CONTRASENA",
                    "Usuario", destino.getIdUsuario(), "Contraseña restablecida; token consumido");
        } catch (RuntimeException e) {
            registrarFallo("RESTABLECER_CONTRASENA", destino, motivo);
            throw e;
        }
    }

    private void registrarFallo(String accion, Usuario destino, String motivo) {
        // Estas rutas no autentican al solicitante: no atribuirle la identidad de la cuenta afectada.
        auditoriaService.registrarFallo(null, null, ModuloAuditoria.AUTENTICACION, accion, "Usuario",
                destino == null ? null : destino.getIdUsuario(), motivo, false);
    }

    private void sendEmail(String toEmail, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Recuperación de Contraseña");
        message.setText("Su token de recuperación es: " + token + "\nExpira en 15 minutos.");

        if (mailSender != null) {
            mailSender.send(message);
        } else {
            System.out.println("[CORREO SIMULADO] Para: " + toEmail + " | Token: " + token);
        }
    }
}
