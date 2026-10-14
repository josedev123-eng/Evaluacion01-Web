package com.evaluacion01.tecsup.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ReniecConfig {

    // Tiempos cortos: si el servicio externo no responde, el formulario se completa a mano.
    @Bean
    public RestClient reniecRestClient() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(3000);
        fabrica.setReadTimeout(5000);
        return RestClient.builder().requestFactory(fabrica).build();
    }
}
