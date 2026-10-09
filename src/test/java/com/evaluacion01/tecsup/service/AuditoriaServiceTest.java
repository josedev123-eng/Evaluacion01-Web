package com.evaluacion01.tecsup.service;

import com.evaluacion01.tecsup.audit.AuditoriaContext;
import com.evaluacion01.tecsup.audit.ModuloAuditoria;
import com.evaluacion01.tecsup.audit.ResultadoAuditoria;
import com.evaluacion01.tecsup.entity.AuditoriaLog;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.AuditoriaLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    @Mock private AuditoriaLogRepository repository;
    @Mock private AuditoriaFallosService fallos;
    private AuditoriaService service;

    @BeforeEach
    void preparar() {
        service = new AuditoriaService(repository, fallos,
                Clock.fixed(Instant.parse("2026-10-09T10:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void limpiarContexto() {
        AuditoriaContext.limpiar();
    }

    @Test
    void capturaIdentidadFechaUtcYMetadatosSinDependerDeLaEntidadDespues() {
        Usuario operador = new Usuario();
        operador.setIdUsuario(7L);
        operador.setUsuario(" operador ");
        AuditoriaContext.establecer(new AuditoriaContext.DatosPeticion("127.0.0.1", "POST", "/usuarios/guardar"));

        service.registrarExito(operador, ModuloAuditoria.USUARIOS, "CREAR_USUARIO", "Usuario", 9L, "Datos permitidos");
        operador.setUsuario("otro");

        AuditoriaLog evento = eventoExitoso();
        assertThat(evento.getFechaHora()).isEqualTo(LocalDateTime.of(2026, 10, 9, 10, 0));
        assertThat(evento.getIdUsuarioEjecutor()).isEqualTo(7L);
        assertThat(evento.getUsuarioEjecutor()).isEqualTo("operador");
        assertThat(evento.getIdEntidad()).isEqualTo(9L);
        assertThat(evento.getResultado()).isEqualTo(ResultadoAuditoria.EXITO);
        assertThat(evento.getIp()).isEqualTo("127.0.0.1");
        assertThat(evento.getMetodoHttp()).isEqualTo("POST");
        assertThat(evento.getRuta()).isEqualTo("/usuarios/guardar");
    }

    @Test
    void permiteOperacionesSinHttpYUsaEjecutorAnonimo() {
        service.registrarExito(null, ModuloAuditoria.ROLES, "CREAR_ROL", "Rol", 3, null);

        AuditoriaLog evento = eventoExitoso();
        assertThat(evento.getUsuarioEjecutor()).isEqualTo("ANONIMO");
        assertThat(evento.getIdUsuarioEjecutor()).isNull();
        assertThat(evento.getIdEntidad()).isEqualTo(3L);
        assertThat(evento.getIp()).isNull();
        assertThat(evento.getRuta()).isNull();
    }

    @Test
    void limitaCamposYEliminaCaracteresDeControl() {
        Usuario operador = new Usuario();
        operador.setUsuario("u".repeat(150));
        AuditoriaContext.establecer(new AuditoriaContext.DatosPeticion("i".repeat(100), "m".repeat(30), "r".repeat(300)));

        service.registrarExito(operador, ModuloAuditoria.USUARIOS, "a".repeat(80), "e".repeat(80), null,
                "inicio\r\n" + "d".repeat(1200));

        AuditoriaLog evento = eventoExitoso();
        assertThat(evento.getUsuarioEjecutor()).hasSize(100);
        assertThat(evento.getAccion()).hasSize(50);
        assertThat(evento.getEntidad()).hasSize(50);
        assertThat(evento.getDetalle()).hasSize(1000).doesNotContain("\r", "\n");
        assertThat(evento.getIp()).hasSize(45);
        assertThat(evento.getMetodoHttp()).hasSize(10);
        assertThat(evento.getRuta()).hasSize(255);
    }

    @Test
    void losIntentosFallidosSeEnvianAlEscritorIndependiente() {
        service.registrarFallo(null, " correo@pruebas.local ", ModuloAuditoria.AUTENTICACION,
                "LOGIN", "Usuario", null, "CREDENCIALES_INVALIDAS", false);

        ArgumentCaptor<AuditoriaLog> captor = ArgumentCaptor.forClass(AuditoriaLog.class);
        verify(fallos).guardar(captor.capture());
        assertThat(captor.getValue().getUsuarioEjecutor()).isEqualTo("correo@pruebas.local");
        assertThat(captor.getValue().getResultado()).isEqualTo(ResultadoAuditoria.FALLO);
        verifyNoInteractions(repository);
    }

    @Test
    void distingueAccesosDenegadosDeErroresDeValidacion() {
        service.registrarFallo(null, null, ModuloAuditoria.ROLES, "EDITAR_ROL", "Rol", 2, "SIN_PERMISO", true);

        ArgumentCaptor<AuditoriaLog> captor = ArgumentCaptor.forClass(AuditoriaLog.class);
        verify(fallos).guardar(captor.capture());
        assertThat(captor.getValue().getResultado()).isEqualTo(ResultadoAuditoria.DENEGADO);
    }

    @Test
    void unFalloDelRegistroNoSustituyeElRechazoOriginal() {
        doThrow(new IllegalStateException("Base no disponible")).when(fallos).guardar(any());

        assertThatCode(() -> service.registrarFallo(null, null, ModuloAuditoria.AUTENTICACION,
                "LOGIN", "Usuario", null, "CREDENCIALES_INVALIDAS", false)).doesNotThrowAnyException();
    }

    private AuditoriaLog eventoExitoso() {
        ArgumentCaptor<AuditoriaLog> captor = ArgumentCaptor.forClass(AuditoriaLog.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
