package com.evaluacion01.tecsup.service;

import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

    public void processForgotPassword(String email) {
        Usuario usuario = usuarioRepository.findByCorreo(email)
                .orElseThrow(() -> new RuntimeException("No existe un usuario con el correo ingresado"));

        String token = UUID.randomUUID().toString();
        usuario.setResetToken(token);
        usuario.setResetTokenExpiry(LocalDateTime.now().plusMinutes(15));
        usuarioRepository.save(usuario);

        sendEmail(usuario.getCorreo(), token);
    }

    public void processResetPassword(String token, String newPassword) {
        Usuario usuario = usuarioRepository.findByResetToken(token)
                .orElseThrow(() -> new RuntimeException("El token de recuperación es inválido"));

        if (usuario.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("El token de recuperación ha expirado");
        }

        usuario.setContrasena(passwordEncoder.encode(newPassword));
        usuario.setResetToken(null);
        usuario.setResetTokenExpiry(null);
        usuarioRepository.save(usuario);
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
