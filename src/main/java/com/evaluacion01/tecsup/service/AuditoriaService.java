package com.evaluacion01.tecsup.service;

import com.evaluacion01.tecsup.audit.AuditoriaContext;
import com.evaluacion01.tecsup.audit.ModuloAuditoria;
import com.evaluacion01.tecsup.audit.ResultadoAuditoria;
import com.evaluacion01.tecsup.entity.AuditoriaLog;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.AuditoriaLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditoriaService.class);

    private final AuditoriaLogRepository repository;
    private final AuditoriaFallosService fallosService;
    private final Clock auditoriaClock;

    // El éxito y la modificación crítica se confirman o se revierten juntos.
    @Transactional
    public void registrarExito(Usuario operador, ModuloAuditoria modulo, String accion,
                              String entidad, Number idEntidad, String detalle) {
        repository.save(crear(operador, null, modulo, accion, entidad, idEntidad, detalle, ResultadoAuditoria.EXITO));
    }

    public void registrarFallo(Usuario operador, String identificadorIntentado, ModuloAuditoria modulo,
                              String accion, String entidad, Number idEntidad, String detalle, boolean denegado) {
        AuditoriaLog evento = crear(operador, identificadorIntentado, modulo, accion, entidad, idEntidad, detalle,
                denegado ? ResultadoAuditoria.DENEGADO : ResultadoAuditoria.FALLO);
        try {
            fallosService.guardar(evento);
        } catch (RuntimeException e) {
            // No sustituir el error original ni imprimir parámetros SQL o datos sensibles.
            LOGGER.error("No se pudo guardar el intento de auditoría [{}][{}]", modulo, accion);
        }
    }

    public void registrarFalloOperacion(Usuario operador, ModuloAuditoria modulo, String accion,
                                       String entidad, Number idEntidad, RuntimeException causa) {
        boolean denegado = causa instanceof AccessDeniedException;
        registrarFallo(operador, null, modulo, accion, entidad, idEntidad,
                denegado ? "SIN_PERMISO" : "OPERACION_RECHAZADA", denegado);
    }

    private AuditoriaLog crear(Usuario operador, String identificadorIntentado, ModuloAuditoria modulo,
                               String accion, String entidad, Number idEntidad, String detalle,
                               ResultadoAuditoria resultado) {
        String ejecutor = operador == null ? identificadorIntentado : operador.getUsuario();
        if (ejecutor == null || ejecutor.isBlank()) {
            ejecutor = "ANONIMO";
        }
        AuditoriaContext.DatosPeticion peticion = AuditoriaContext.obtener();
        return new AuditoriaLog(LocalDateTime.now(auditoriaClock),
                operador == null ? null : operador.getIdUsuario(), limpiar(ejecutor, 100),
                Objects.requireNonNull(modulo), limpiar(Objects.requireNonNull(accion), 50),
                limpiar(entidad, 50), idEntidad == null ? null : idEntidad.longValue(), limpiar(detalle, 1000),
                limpiar(peticion.ip(), 45), limpiar(peticion.metodo(), 10), limpiar(peticion.ruta(), 255), resultado);
    }

    private String limpiar(String valor, int maximo) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.replaceAll("[\\p{Cntrl}]", " ").trim();
        return limpio.length() > maximo ? limpio.substring(0, maximo) : limpio;
    }
}
