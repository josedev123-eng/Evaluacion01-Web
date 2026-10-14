package com.evaluacion01.tecsup.service;

import com.evaluacion01.tecsup.dto.PersonaReniec;
import com.evaluacion01.tecsup.exception.ReniecNoDisponibleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ReniecServiceTest {

    private static final String URL = "https://api.ejemplo.test/v2/reniec/dni";

    private MockRestServiceServer servidor;
    private RestClient cliente;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        cliente = builder.build();
    }

    @Test
    void devuelveNombresYApellidosEnFormatoTitulo() {
        servidor.expect(requestTo(URL + "?numero=12345678"))
                .andExpect(header("Authorization", "Bearer token-prueba"))
                .andRespond(withSuccess("{\"nombres\":\"JUAN CARLOS\",\"apellidoPaterno\":\"PEREZ\","
                        + "\"apellidoMaterno\":\"DE LA CRUZ\"}", MediaType.APPLICATION_JSON));

        PersonaReniec persona = new ReniecService(cliente, URL, "token-prueba", false).consultar("12345678").orElseThrow();

        assertThat(persona.nombres()).isEqualTo("Juan Carlos");
        assertThat(persona.apellidos()).isEqualTo("Perez De La Cruz");
        servidor.verify();
    }

    @Test
    void unDniInexistenteDevuelveVacio() {
        servidor.expect(requestTo(URL + "?numero=00000000")).andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

        assertThat(new ReniecService(cliente, URL, "token-prueba", false).consultar("00000000")).isEmpty();
    }

    @Test
    void unaRespuestaSinNombresDevuelveVacio() {
        servidor.expect(requestTo(URL + "?numero=11111111")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(new ReniecService(cliente, URL, "token-prueba", false).consultar("11111111")).isEmpty();
    }

    @Test
    void unTokenRechazadoSeTrataComoServicioNoDisponible() {
        servidor.expect(requestTo(URL + "?numero=12345678")).andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> new ReniecService(cliente, URL, "token-malo", false).consultar("12345678"))
                .isInstanceOf(ReniecNoDisponibleException.class);
    }

    @Test
    void unErrorDelProveedorSeTrataComoServicioNoDisponible() {
        servidor.expect(requestTo(URL + "?numero=12345678")).andRespond(withServerError());

        assertThatThrownBy(() -> new ReniecService(cliente, URL, "token-prueba", false).consultar("12345678"))
                .isInstanceOf(ReniecNoDisponibleException.class);
    }

    @Test
    void modoSimuladoSinTokenDevuelveUnaPersonaFicticiaSinLlamarAlProveedor() {
        ReniecService servicio = new ReniecService(cliente, URL, "", true);

        assertThat(servicio.habilitado()).isTrue();
        PersonaReniec primera = servicio.consultar("12345678").orElseThrow();
        assertThat(primera.simulado()).isTrue();
        assertThat(primera.nombres()).isNotBlank();
        assertThat(primera.apellidos()).isNotBlank();
        assertThat(servicio.consultar("12345678").orElseThrow()).isEqualTo(primera);
        servidor.verify();
    }

    @Test
    void conTokenSeUsaElProveedorAunqueElModoSimuladoEsteActivo() {
        servidor.expect(requestTo(URL + "?numero=12345678"))
                .andRespond(withSuccess("{\"nombres\":\"ANA\",\"apellidoPaterno\":\"RIOS\"}", MediaType.APPLICATION_JSON));

        PersonaReniec persona = new ReniecService(cliente, URL, "token-prueba", true).consultar("12345678").orElseThrow();

        assertThat(persona.simulado()).isFalse();
        assertThat(persona.nombres()).isEqualTo("Ana");
        servidor.verify();
    }

    @Test
    void sinTokenQuedaDesactivadoYNoLlamaAlProveedor() {
        ReniecService servicio = new ReniecService(cliente, URL, "  ", false);

        assertThat(servicio.habilitado()).isFalse();
        assertThatThrownBy(() -> servicio.consultar("12345678")).isInstanceOf(ReniecNoDisponibleException.class);
        servidor.verify();
    }
}
