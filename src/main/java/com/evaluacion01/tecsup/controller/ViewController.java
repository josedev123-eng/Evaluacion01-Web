package com.evaluacion01.tecsup.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewController {

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "forgot-password"; // Carga forgot-password.html
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage() {
        return "reset-password"; // Carga reset-password.html
    }
}