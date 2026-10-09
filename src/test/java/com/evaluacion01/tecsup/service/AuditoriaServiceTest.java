package com.evaluacion01.tecsup.service;

import com.evaluacion01.tecsup.audit.AuditoriaContext;
import com.evaluacion01.tecsup.entity.AuditoriaLog;
import com.evaluacion01.tecsup.repository.AuditoriaLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    @Mock
    private AuditoriaLogRepository auditoriaLogRepository;

    @InjectMocks
    private AuditoriaService auditoriaService;

    @AfterEach
    void limpiarContexto() {
        AuditoriaContext.clear();
    }

    @Test
    void debeGuardarLaOperacionCriticaConLosCamposEsperados() {
        auditoriaService.registrar("jdoe", AuditoriaService.MODULO_USUARIOS, "CREAR_USUARIO",
                "Usuario", 10L, "Usuario creado: Juan Perez", AuditoriaService.RESULTADO_EXITO);

        ArgumentCaptor<AuditoriaLog> captor = ArgumentCaptor.forClass(AuditoriaLog.class);
        verify(auditoriaLogRepository).save(captor.capture());

        AuditoriaLog registro = captor.getValue();
        assertThat(registro.getFechaHora()).isNotNull();
        assertThat(registro.getUsuarioEjecutor()).isEqualTo("jdoe");
        assertThat(registro.getModulo()).isEqualTo("USUARIOS");
        assertThat(registro.getAccion()).isEqualTo("CREAR_USUARIO");
        assertThat(registro.getEntidad()).isEqualTo("Usuario");
        assertThat(registro.getIdEntidad()).isEqualTo(10L);
        assertThat(registro.getDetalle()).isEqualTo("Usuario creado: Juan Perez");
        assertThat(registro.getResultado()).isEqualTo("EXITO");
    }

    @Test
    void debeUsarElUsuarioDeLaSesionCuandoNoSeIndiqueEjecutor() {
        AuditoriaContext.set("edu", "10.0.0.7");

        auditoriaService.registrar(AuditoriaService.MODULO_ROLES, "EDITAR_ROL", "Rol", 2L, "nombre: 'A' -> 'B'");

        ArgumentCaptor<AuditoriaLog> captor = ArgumentCaptor.forClass(AuditoriaLog.class);
        verify(auditoriaLogRepository).save(captor.capture());
        assertThat(captor.getValue().getUsuarioEjecutor()).isEqualTo("edu");
        assertThat(captor.getValue().getIp()).isEqualTo("10.0.0.7");
        assertThat(captor.getValue().getResultado()).isEqualTo("EXITO");
    }

    @Test
    void debeRegistrarComoSistemaCuandoNoHaySesion() {
        auditoriaService.registrar(AuditoriaService.MODULO_AUTENTICACION, "LOGIN_FALLIDO",
                "Usuario", null, "Sin usuario en sesión");

        ArgumentCaptor<AuditoriaLog> captor = ArgumentCaptor.forClass(AuditoriaLog.class);
        verify(auditoriaLogRepository).save(captor.capture());
        assertThat(captor.getValue().getUsuarioEjecutor()).isEqualTo("sistema");
        assertThat(captor.getValue().getIp()).isNull();
    }

    @Test
    void debeMarcarResultadoFallidoEnIntentosRechazados() {
        auditoriaService.registrarFallo("jdoe", AuditoriaService.MODULO_AUTENTICACION, "LOGIN_FALLIDO",
                "Usuario", null, "Contraseña incorrecta");

        ArgumentCaptor<AuditoriaLog> captor = ArgumentCaptor.forClass(AuditoriaLog.class);
        verify(auditoriaLogRepository).save(captor.capture());
        assertThat(captor.getValue().getResultado()).isEqualTo("FALLO");
        assertThat(captor.getValue().getDetalle()).isEqualTo("Contraseña incorrecta");
    }

    @Test
    void noDebePropagarErroresCuandoFallaElRegistro() {
        when(auditoriaLogRepository.save(any(AuditoriaLog.class))).thenThrow(new RuntimeException("BD caída"));

        assertThatCode(() -> auditoriaService.registrar("jdoe", AuditoriaService.MODULO_USUARIOS,
                "CREAR_USUARIO", "Usuario", 1L, "Detalle", AuditoriaService.RESULTADO_EXITO))
                .doesNotThrowAnyException();
    }

    @Test
    void debeAcortarElDetalleQueExcedaElTamanoPermitido() {
        String detalleLargo = "x".repeat(1500);

        auditoriaService.registrar("jdoe", AuditoriaService.MODULO_USUARIOS, "EDITAR_USUARIO",
                "Usuario", 1L, detalleLargo, AuditoriaService.RESULTADO_EXITO);

        ArgumentCaptor<AuditoriaLog> captor = ArgumentCaptor.forClass(AuditoriaLog.class);
        verify(auditoriaLogRepository).save(captor.capture());
        assertThat(captor.getValue().getDetalle()).hasSize(1000);
    }
}
