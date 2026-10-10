package com.evaluacion01.tecsup;

import com.evaluacion01.tecsup.audit.ModuloAuditoria;
import com.evaluacion01.tecsup.audit.ResultadoAuditoria;
import com.evaluacion01.tecsup.entity.AuditoriaLog;
import com.evaluacion01.tecsup.repository.AuditoriaLogRepository;
import com.evaluacion01.tecsup.service.AuditoriaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AuditoriaPersistenciaIntegrationTests {

    @Autowired private AuditoriaService service;
    @Autowired private AuditoriaLogRepository logs;
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void limpiarDatosDePrueba() {
        logs.deleteAll();
    }

    @Test
    void persisteUnEventoConFechaUtcYEntidadAfectada() {
        LocalDateTime inicio = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1);

        service.registrarExito(null, ModuloAuditoria.USUARIOS, "CREAR_USUARIO", "Usuario", 42L, "Cuenta creada");

        assertThat(logs.findAll()).hasSize(1);
        AuditoriaLog evento = logs.findAll().get(0);
        assertThat(evento.getIdAuditoria()).isPositive();
        assertThat(evento.getFechaHora()).isAfterOrEqualTo(inicio);
        assertThat(evento.getIdEntidad()).isEqualTo(42L);
        assertThat(evento.getResultado()).isEqualTo(ResultadoAuditoria.EXITO);
    }

    @Test
    void unRollbackNoDejaUnExitoFalso() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.registrarExito(null, ModuloAuditoria.ROLES, "EDITAR_ROL", "Rol", 1, "Rol actualizado");
            status.setRollbackOnly();
        });

        assertThat(logs.count()).isZero();
    }

    @Test
    void conservaElIntentoDenegadoAunqueLaOperacionSeRevierta() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.registrarFallo(null, "operador", ModuloAuditoria.ROLES, "EDITAR_ROL", "Rol", 1,
                    "SIN_PERMISO", true);
            throw new IllegalStateException("Operación rechazada");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(logs.findAll()).hasSize(1);
        assertThat(logs.findAll().get(0).getResultado()).isEqualTo(ResultadoAuditoria.DENEGADO);
    }
}
