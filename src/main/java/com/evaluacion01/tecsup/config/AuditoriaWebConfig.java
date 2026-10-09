package com.evaluacion01.tecsup.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Registra el interceptor de auditoría en todas las rutas sin tocar los
// controladores existentes.
@Configuration
@RequiredArgsConstructor
public class AuditoriaWebConfig implements WebMvcConfigurer {

    private final AuditoriaInterceptor auditoriaInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditoriaInterceptor).addPathPatterns("/**");
    }
}
