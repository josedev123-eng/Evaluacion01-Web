package com.evaluacion01.tecsup.service;

import com.evaluacion01.tecsup.entity.User;
import com.evaluacion01.tecsup.repository.UserRepository;
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
    private UserRepository userRepository;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public void processForgotPassword(String email) {
        User user = userRepository.findByCorreo(email)
                .orElseThrow(() -> new RuntimeException("No existe un usuario con el correo ingresado"));

        String token = UUID.randomUUID().toString();
        user.setResetToken(token);
        user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(15));
        userRepository.save(user);

        sendEmail(user.getCorreo(), token);
    }

    public void processResetPassword(String token, String newPassword) {
        User user = userRepository.findByResetToken(token)
                .orElseThrow(() -> new RuntimeException("El token de recuperación es inválido"));

        if (user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("El token de recuperación ha expirado");
        }

        user.setContrasena(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
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