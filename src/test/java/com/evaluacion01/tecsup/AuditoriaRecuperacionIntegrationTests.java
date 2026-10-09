package com.evaluacion01.tecsup;

import com.evaluacion01.tecsup.audit.ModuloAuditoria;
import com.evaluacion01.tecsup.audit.ResultadoAuditoria;
import com.evaluacion01.tecsup.entity.AuditoriaLog;
import com.evaluacion01.tecsup.entity.Rol;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.AuditoriaLogRepository;
import com.evaluacion01.tecsup.repository.RolRepository;
import com.evaluacion01.tecsup.repository.UsuarioRepository;
import com.evaluacion01.tecsup.service.PasswordRecoveryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditoriaRecuperacionIntegrationTests {

    private static final String TOKEN = "token-secreto-de-recuperacion";
    private static final String CLAVE_NUEVA = "claveNuevaSecreta";

    @Autowired private MockMvc mvc;
    @Autowired private AuditoriaLogRepository logs;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private RolRepository roles;
    @Autowired private PasswordEncoder encoder;
    @Autowired private PasswordRecoveryService recovery;
    @Autowired private PlatformTransactionManager transactionManager;

    private Usuario usuario;
    private Rol rol;
    private String hashInicial;

    @BeforeEach
    void preparar() {
        logs.deleteAll();
        rol = new Rol();
        rol.setNombre("Médico de recuperación auditada");
        rol.setArea("Medicina");
        rol = roles.saveAndFlush(rol);
        usuario = new Usuario();
        usuario.setUsuario("recuperacionAudit");
        usuario.setCorreo("recuperacionAudit@pruebas.local");
        usuario.setNombres("Prueba");
        usuario.setApellidos("Recuperación");
        hashInicial = encoder.encode("claveAnterior");
        usuario.setContrasena(hashInicial);
        usuario.setRol(rol);
        usuario.setRoles(new LinkedHashSet<>(Set.of(rol)));
        usuario.setArea("Medicina");
        usuario = usuarios.saveAndFlush(usuario);
    }

    @AfterEach
    void limpiar() {
        usuarios.deleteById(usuario.getIdUsuario());
        roles.deleteById(rol.getIdRol());
        logs.deleteAll();
    }

    @Test
    void solicitudExitosaRegistraLaCuentaAfectadaPeroNoSuCorreoNiToken() throws Exception {
        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + usuario.getCorreo() + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));

        Usuario guardado = cuenta();
        assertThat(guardado.getResetToken()).isNotBlank();
        AuditoriaLog evento = unico("SOLICITAR_RECUPERACION", ResultadoAuditoria.EXITO);
        assertThat(evento.getIdEntidad()).isEqualTo(usuario.getIdUsuario());
        assertThat(evento.getUsuarioEjecutor()).isEqualTo("ANONIMO");
        assertThat(evento.getIdUsuarioEjecutor()).isNull();
        assertThat(texto(evento)).doesNotContain(guardado.getResetToken());
        assertThat(evento.getRuta()).isEqualTo("/api/v1/auth/forgot-password");
    }

    @Test
    void restablecimientoExitosoSeAuditaSinGuardarClavesNiTokens() throws Exception {
        establecerToken(LocalDateTime.now().plusMinutes(15));

        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(cuerpo()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));

        Usuario guardado = cuenta();
        assertThat(encoder.matches(CLAVE_NUEVA, guardado.getContrasena())).isTrue();
        assertThat(guardado.getResetToken()).isNull();
        AuditoriaLog evento = unico("RESTABLECER_CONTRASENA", ResultadoAuditoria.EXITO);
        assertThat(evento.getIdEntidad()).isEqualTo(usuario.getIdUsuario());
        assertThat(texto(evento)).doesNotContain(guardado.getContrasena());
    }

    @Test
    void correoInexistenteSeRegistraSinCopiarElDatoEnviado() throws Exception {
        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"noExiste@pruebas.local\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));

        AuditoriaLog evento = unico("SOLICITAR_RECUPERACION", ResultadoAuditoria.FALLO);
        assertThat(evento.getDetalle()).isEqualTo("CORREO_NO_REGISTRADO");
        assertThat(evento.getIdEntidad()).isNull();
        assertThat(texto(evento)).doesNotContain("noExiste@pruebas.local");
        assertThat(cuenta().getResetToken()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"inexistente", "expirado", "sinFecha"})
    void tokensInvalidosNoCambianLaClaveYConservanElIntentoFallido(String caso) throws Exception {
        if ("expirado".equals(caso)) establecerToken(LocalDateTime.now().minusMinutes(1));
        if ("sinFecha".equals(caso)) establecerToken(null);

        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(cuerpo()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));

        AuditoriaLog evento = unico("RESTABLECER_CONTRASENA", ResultadoAuditoria.FALLO);
        assertThat(evento.getDetalle()).isEqualTo("inexistente".equals(caso) ? "RECUPERACION_INVALIDA" : "RECUPERACION_EXPIRADA");
        assertThat(cuenta().getContrasena()).isEqualTo(hashInicial);
    }

    @Test
    void unTokenReutilizadoSoloGeneraUnNuevoFallo() throws Exception {
        establecerToken(LocalDateTime.now().plusMinutes(15));
        recovery.processResetPassword(TOKEN, CLAVE_NUEVA);

        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(cuerpo()))
                .andExpect(status().isBadRequest());

        assertThat(logs.findAll()).hasSize(2);
        assertThat(logs.findAll()).filteredOn(evento -> evento.getResultado() == ResultadoAuditoria.EXITO).hasSize(1);
        assertThat(logs.findAll()).filteredOn(evento -> evento.getResultado() == ResultadoAuditoria.FALLO).hasSize(1);
        assertThat(encoder.matches(CLAVE_NUEVA, cuenta().getContrasena())).isTrue();
        logs.findAll().forEach(this::verificarSinSecretos);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"email\":\"correo-invalido\"}"})
    void validacionesDelCorreoTambienSeAuditan(String cuerpo) throws Exception {
        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.email").exists());

        assertThat(unico("SOLICITAR_RECUPERACION", ResultadoAuditoria.FALLO).getDetalle()).isEqualTo("DATOS_INVALIDOS");
        assertThat(cuenta().getResetToken()).isNull();
    }

    @Test
    void unaClaveCortaSeRechazaSinConsumirElTokenNiRegistrarla() throws Exception {
        establecerToken(LocalDateTime.now().plusMinutes(15));

        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + TOKEN + "\",\"newPassword\":\"abc\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.newPassword").exists());

        AuditoriaLog evento = unico("RESTABLECER_CONTRASENA", ResultadoAuditoria.FALLO);
        assertThat(evento.getDetalle()).isEqualTo("DATOS_INVALIDOS");
        assertThat(texto(evento)).doesNotContain("abc");
        assertThat(cuenta().getResetToken()).isEqualTo(TOKEN);
        assertThat(cuenta().getContrasena()).isEqualTo(hashInicial);
    }

    @Test
    void rollbackRevierteLaClaveElConsumoDelTokenYElEventoExitoso() {
        establecerToken(LocalDateTime.now().plusMinutes(15));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            recovery.processResetPassword(TOKEN, CLAVE_NUEVA);
            status.setRollbackOnly();
        });

        assertThat(cuenta().getContrasena()).isEqualTo(hashInicial);
        assertThat(cuenta().getResetToken()).isEqualTo(TOKEN);
        assertThat(logs.count()).isZero();
    }

    @Test
    void elServicioAuditaSolicitudesFueraDeHttp() {
        recovery.processForgotPassword(usuario.getCorreo());

        AuditoriaLog evento = unico("SOLICITAR_RECUPERACION", ResultadoAuditoria.EXITO);
        assertThat(evento.getRuta()).isNull();
        assertThat(evento.getIp()).isNull();
    }

    private AuditoriaLog unico(String accion, ResultadoAuditoria resultado) {
        assertThat(logs.findAll()).hasSize(1);
        AuditoriaLog evento = logs.findAll().get(0);
        assertThat(evento.getModulo()).isEqualTo(ModuloAuditoria.AUTENTICACION);
        assertThat(evento.getAccion()).isEqualTo(accion);
        assertThat(evento.getResultado()).isEqualTo(resultado);
        verificarSinSecretos(evento);
        return evento;
    }

    private void verificarSinSecretos(AuditoriaLog evento) {
        assertThat(texto(evento)).doesNotContain(TOKEN, CLAVE_NUEVA, "claveAnterior", hashInicial, usuario.getCorreo());
    }

    private String texto(AuditoriaLog evento) {
        return Stream.of(evento.getUsuarioEjecutor(), evento.getAccion(), evento.getDetalle(), evento.getEntidad(),
                        evento.getIp(), evento.getRuta()).filter(Objects::nonNull).collect(Collectors.joining(" "));
    }

    private Usuario cuenta() {
        return usuarios.findById(usuario.getIdUsuario()).orElseThrow();
    }

    private void establecerToken(LocalDateTime expiracion) {
        usuario.setResetToken(TOKEN);
        usuario.setResetTokenExpiry(expiracion);
        usuario = usuarios.saveAndFlush(usuario);
    }

    private String cuerpo() {
        return "{\"token\":\"" + TOKEN + "\",\"newPassword\":\"" + CLAVE_NUEVA + "\"}";
    }
}
