package com.evaluacion01.tecsup;

import com.evaluacion01.tecsup.entity.Rol;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.RolRepository;
import com.evaluacion01.tecsup.repository.UsuarioRepository;
import com.evaluacion01.tecsup.service.AuthService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RecuperacionPasswordIntegrationTests {

    private static final String TOKEN = "token-recuperacion-prueba";

    @Autowired private MockMvc mvc;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private RolRepository roles;
    @Autowired private PasswordEncoder encoder;
    @Autowired private AuthService authService;
    @Autowired private EntityManager entityManager;

    private Usuario usuario;
    private String hashInicial;
    private Integer idRolPrincipal;
    private Integer idRolAdicional;

    @BeforeEach
    void prepararDatos() {
        Rol principal = crearRol("Médico de recuperación");
        Rol adicional = crearRol("Auditor de recuperación");
        idRolPrincipal = principal.getIdRol();
        idRolAdicional = adicional.getIdRol();
        hashInicial = encoder.encode("claveAnterior");
        usuario = new Usuario();
        usuario.setNombres("Leonel");
        usuario.setApellidos("Prueba");
        usuario.setCorreo("recuperacion@pruebas.local");
        usuario.setUsuario("recuperacion");
        usuario.setContrasena(hashInicial);
        usuario.setArea("Medicina");
        usuario.setRol(principal);
        usuario.setRoles(new LinkedHashSet<>(Set.of(principal, adicional)));
        usuario = usuarios.saveAndFlush(usuario);
    }

    @ParameterizedTest
    @CsvSource({
            "/forgot-password, forgot-password",
            "/reset-password, reset-password",
            "/login, login"
    })
    void lasVistasPublicasSeRenderizanSinSesion(String ruta, String plantilla) throws Exception {
        mvc.perform(get(ruta))
                .andExpect(status().isOk())
                .andExpect(view().name(plantilla))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    void solicitarRecuperacionSinCsrfGuardaElTokenEnElUsuarioMultirrol() throws Exception {
        LocalDateTime inicio = LocalDateTime.now();

        solicitarToken(usuario.getCorreo());

        Usuario guardado = usuarioPersistido();
        assertThat(guardado.getResetToken()).isNotBlank();
        assertThat(guardado.getResetTokenExpiry())
                .isAfterOrEqualTo(inicio.plusMinutes(15).minusSeconds(1))
                .isBeforeOrEqualTo(LocalDateTime.now().plusMinutes(15).plusSeconds(1));
        assertThat(guardado.getContrasena()).isEqualTo(hashInicial);
        comprobarRoles(guardado);
    }

    @Test
    void unaNuevaSolicitudReemplazaElTokenAnterior() throws Exception {
        establecerToken(LocalDateTime.now().plusMinutes(1));

        solicitarToken(usuario.getCorreo());

        Usuario guardado = usuarioPersistido();
        assertThat(guardado.getResetToken()).isNotBlank().isNotEqualTo(TOKEN);
        assertThat(guardado.getResetTokenExpiry()).isAfter(LocalDateTime.now().plusMinutes(14));
        assertThat(usuarios.findByResetToken(TOKEN)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"email\":\"\"}", "{\"email\":\"correo-invalido\"}"})
    void rechazaCorreosVaciosOInvalidosSinModificarLaCuenta(String cuerpo) throws Exception {
        mvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.email").exists());

        Usuario guardado = usuarioPersistido();
        assertThat(guardado.getResetToken()).isNull();
        assertThat(guardado.getContrasena()).isEqualTo(hashInicial);
    }

    @Test
    void unCorreoInexistenteDevuelveUnErrorControlado() throws Exception {
        mvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"inexistente@pruebas.local\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("No existe un usuario con el correo ingresado"));

        assertThat(usuarioPersistido().getResetToken()).isNull();
    }

    @Test
    void restablecerSinCsrfCifraLaClaveConservaRolesYPermiteIniciarSesion() throws Exception {
        establecerToken(LocalDateTime.now().plusMinutes(15));

        restablecerClave("claveNueva");

        Usuario guardado = usuarioPersistido();
        assertThat(encoder.matches("claveNueva", guardado.getContrasena())).isTrue();
        assertThat(guardado.getContrasena()).isNotEqualTo("claveNueva").isNotEqualTo(hashInicial);
        assertThat(guardado.getResetToken()).isNull();
        assertThat(guardado.getResetTokenExpiry()).isNull();
        comprobarRoles(guardado);
        assertThat(authService.autenticar(guardado.getCorreo(), "claveNueva").getIdUsuario())
                .isEqualTo(guardado.getIdUsuario());
        assertThatThrownBy(() -> authService.autenticar(guardado.getUsuario(), "claveAnterior"))
                .hasMessage("La contraseña es incorrecta.");
    }

    @Test
    void elTokenConsumidoNoSePuedeUsarUnaSegundaVez() throws Exception {
        establecerToken(LocalDateTime.now().plusMinutes(15));
        restablecerClave("claveNueva");

        mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRestablecimiento("claveDistinta")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        Usuario guardado = usuarioPersistido();
        assertThat(encoder.matches("claveNueva", guardado.getContrasena())).isTrue();
        assertThat(encoder.matches("claveDistinta", guardado.getContrasena())).isFalse();
    }

    @Test
    void unTokenExpiradoNoModificaLaClave() throws Exception {
        establecerToken(LocalDateTime.now().minusMinutes(1));

        mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRestablecimiento("claveNueva")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("El token de recuperación ha expirado"));

        assertThat(usuarioPersistido().getContrasena()).isEqualTo(hashInicial);
    }

    @Test
    void unTokenInexistenteDevuelveUnErrorControlado() throws Exception {
        mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRestablecimiento("claveNueva")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("El token de recuperación es inválido"));

        assertThat(usuarioPersistido().getContrasena()).isEqualTo(hashInicial);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"token\":\"\",\"newPassword\":\"claveNueva\"}",
            "{\"token\":\"token-recuperacion-prueba\",\"newPassword\":\"abc\"}"
    })
    void validaElTokenYLaLongitudDeLaClaveSinConsumirElToken(String cuerpo) throws Exception {
        establecerToken(LocalDateTime.now().plusMinutes(15));

        mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$").isNotEmpty());

        Usuario guardado = usuarioPersistido();
        assertThat(guardado.getResetToken()).isEqualTo(TOKEN);
        assertThat(guardado.getContrasena()).isEqualTo(hashInicial);
    }

    @Test
    void recuperarLaClaveNoReactivaUnaCuentaDesactivada() throws Exception {
        usuario.setEstado(false);
        establecerToken(LocalDateTime.now().plusMinutes(15));

        restablecerClave("claveNueva");

        Usuario guardado = usuarioPersistido();
        assertThat(guardado.getEstado()).isFalse();
        comprobarRoles(guardado);
        assertThatThrownBy(() -> authService.autenticar(guardado.getUsuario(), "claveNueva"))
                .hasMessage("Su cuenta se encuentra inactiva.");
    }

    private Rol crearRol(String nombre) {
        Rol rol = new Rol();
        rol.setNombre(nombre);
        rol.setArea("Medicina");
        return roles.save(rol);
    }

    private void establecerToken(LocalDateTime expiracion) {
        usuario.setResetToken(TOKEN);
        usuario.setResetTokenExpiry(expiracion);
        usuarios.saveAndFlush(usuario);
    }

    private void solicitarToken(String correo) throws Exception {
        mvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + correo + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private void restablecerClave(String clave) throws Exception {
        mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRestablecimiento(clave)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private String cuerpoRestablecimiento(String clave) {
        return "{\"token\":\"" + TOKEN + "\",\"newPassword\":\"" + clave + "\"}";
    }

    private Usuario usuarioPersistido() {
        usuarios.flush();
        entityManager.clear();
        return usuarios.findById(usuario.getIdUsuario()).orElseThrow();
    }

    private void comprobarRoles(Usuario guardado) {
        assertThat(guardado.getRol().getIdRol()).isEqualTo(idRolPrincipal);
        assertThat(guardado.getRoles()).extracting(Rol::getIdRol)
                .containsExactlyInAnyOrder(idRolPrincipal, idRolAdicional);
    }
}
