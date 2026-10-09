package com.evaluacion01.tecsup.service;

import com.evaluacion01.tecsup.audit.AuditoriaContext;
import com.evaluacion01.tecsup.entity.AuditoriaLog;
import com.evaluacion01.tecsup.repository.AuditoriaLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

// RF-AUD-01: servicio base de auditoría. Nunca lanza excepciones hacia afuera:
// si el registro de log falla, la operación crítica continúa con normalidad.
@Service
@RequiredArgsConstructor
public class AuditoriaService {

    public static final String MODULO_AUTENTICACION = "AUTENTICACION";
    public static final String MODULO_USUARIOS = "USUARIOS";
    public static final String MODULO_ROLES = "ROLES";
    public static final String MODULO_PERMISOS = "PERMISOS";

    public static final String RESULTADO_EXITO = "EXITO";
    public static final String RESULTADO_FALLO = "FALLO";

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditoriaService.class);

    private final AuditoriaLogRepository auditoriaLogRepository;

    // Registra usando el usuario de sesión capturado por AuditoriaContext.
    public void registrar(String modulo, String accion, String entidad, Long idEntidad, String detalle) {
        registrar(AuditoriaContext.getUsuario(), modulo, accion, entidad, idEntidad, detalle, RESULTADO_EXITO);
    }

    public void registrarFallo(String usuarioEjecutor, String modulo, String accion, String entidad,
                               Long idEntidad, String detalle) {
        registrar(usuarioEjecutor, modulo, accion, entidad, idEntidad, detalle, RESULTADO_FALLO);
    }

    public void registrar(String usuarioEjecutor, String modulo, String accion, String entidad,
                          Long idEntidad, String detalle, String resultado) {
        try {
            AuditoriaLog registro = new AuditoriaLog();
            registro.setFechaHora(LocalDateTime.now());
            registro.setUsuarioEjecutor(acortar(usuarioEjecutor, 50));
            registro.setModulo(modulo);
            registro.setAccion(accion);
            registro.setEntidad(entidad);
            registro.setIdEntidad(idEntidad);
            registro.setDetalle(acortar(detalle, 1000));
            registro.setIp(AuditoriaContext.getIp());
            registro.setResultado(resultado);
            auditoriaLogRepository.save(registro);
        } catch (Exception e) {
            LOGGER.error("No se pudo registrar la auditoría [{}][{}]: {}", modulo, accion, e.getMessage());
        }
    }

    public List<AuditoriaLog> listar() {
        return auditoriaLogRepository.findAllByOrderByFechaHoraDesc();
    }

    private String acortar(String valor, int maximo) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.length() > maximo ? limpio.substring(0, maximo) : limpio;
    }
}
