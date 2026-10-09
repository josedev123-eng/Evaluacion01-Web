package com.evaluacion01.tecsup.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final SesionActivaInterceptor sesionActivaInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(sesionActivaInterceptor)
                .excludePathPatterns("/css/**", "/js/**", "/images/**", "/error", "/logout");
    }
}
