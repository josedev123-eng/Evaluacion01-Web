package com.evaluacion01.tecsup.service;

import com.evaluacion01.tecsup.dto.PersonaReniec;
import com.evaluacion01.tecsup.exception.ReniecNoDisponibleException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

// Consulta de DNI. Con token usa un proveedor externo (por defecto apis.net.pe).
// Sin token y con reniec.simulado=true devuelve nombres ficticios de demostración,
// marcados como simulados; sin ninguno de los dos queda desactivada.
@Service
public class ReniecService {

    private static final Locale ES_PE = Locale.forLanguageTag("es-PE");

    private static final List<String> NOMBRES_DEMO = List.of("Juan Carlos", "María Elena", "Luis Alberto",
            "Rosa Isabel", "José Miguel", "Ana Lucía", "Carlos Eduardo", "Patricia Milagros");
    private static final List<String> APELLIDOS_DEMO = List.of("Quispe Mamani", "Flores Huamán", "Rojas Condori",
            "Torres Vargas", "Chávez Salazar", "Mendoza Paredes", "Ramos Gutiérrez", "Castillo Rivera");

    private final RestClient cliente;
    private final String url;
    private final String token;
    private final boolean simulado;

    public ReniecService(@Qualifier("reniecRestClient") RestClient cliente,
                         @Value("${reniec.api.url:https://api.apis.net.pe/v2/reniec/dni}") String url,
                         @Value("${reniec.api.token:}") String token,
                         @Value("${reniec.simulado:false}") boolean simulado) {
        this.cliente = cliente;
        this.url = url;
        this.token = token == null ? "" : token.trim();
        this.simulado = simulado;
    }

    public boolean habilitado() {
        return !token.isEmpty() || simulado;
    }

    // El DNI se valida antes (8 dígitos) y no se escribe en ningún registro.
    public Optional<PersonaReniec> consultar(String dni) {
        if (!habilitado()) {
            throw new ReniecNoDisponibleException("La consulta a RENIEC no está configurada.");
        }
        if (token.isEmpty()) {
            return Optional.of(personaDeDemostracion(dni));
        }
        try {
            JsonNode respuesta = cliente.get()
                    .uri(url + "?numero={dni}", dni)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(JsonNode.class);
            if (respuesta == null || !respuesta.hasNonNull("nombres")) {
                return Optional.empty();
            }
            String apellidos = (texto(respuesta, "apellidoPaterno") + " " + texto(respuesta, "apellidoMaterno")).trim();
            return Optional.of(new PersonaReniec(formatear(texto(respuesta, "nombres")), formatear(apellidos), false));
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.UnprocessableEntity e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new ReniecNoDisponibleException("No se pudo consultar RENIEC en este momento.", e);
        }
    }

    // Determinista: el mismo DNI siempre da la misma persona ficticia.
    private static PersonaReniec personaDeDemostracion(String dni) {
        int base = Integer.parseInt(dni);
        String nombres = NOMBRES_DEMO.get(Math.floorMod(base, NOMBRES_DEMO.size()));
        String apellidos = APELLIDOS_DEMO.get(Math.floorMod(base / 7, APELLIDOS_DEMO.size()));
        return new PersonaReniec(nombres, apellidos, true);
    }

    private static String texto(JsonNode nodo, String campo) {
        return nodo.hasNonNull(campo) ? nodo.get(campo).asText("") : "";
    }

    // RENIEC responde en mayúsculas: "JUAN CARLOS" -> "Juan Carlos".
    private static String formatear(String texto) {
        return Arrays.stream(texto.trim().toLowerCase(ES_PE).split("\\s+"))
                .filter(palabra -> !palabra.isEmpty())
                .map(palabra -> Character.toUpperCase(palabra.charAt(0)) + palabra.substring(1))
                .collect(Collectors.joining(" "));
    }
}
