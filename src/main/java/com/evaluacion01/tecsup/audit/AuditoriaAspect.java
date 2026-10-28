package com.evaluacion01.tecsup.audit;

import com.evaluacion01.tecsup.entity.Cita;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.service.AuditoriaService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

// AOP: rodea cada método con @Auditable. Si termina bien guarda un EXITO en la bitácora;
// si lanza una excepción guarda el FALLO (o DENEGADO) y la vuelve a lanzar.
// Va por fuera de la transacción del servicio, así un rollback se registra como fallo.
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class AuditoriaAspect {

    private final AuditoriaService auditoriaService;

    @Around("@annotation(com.evaluacion01.tecsup.audit.Auditable)")
    public Object auditar(ProceedingJoinPoint punto) throws Throwable {
        MethodSignature firma = (MethodSignature) punto.getSignature();
        Auditable auditable = firma.getMethod().getAnnotation(Auditable.class);
        Usuario operador = buscarOperador(punto.getArgs());
        Long idEntidad = buscarId(punto.getArgs(), null);
        try {
            Object resultado = punto.proceed();
            auditoriaService.registrarExito(operador, auditable.modulo(), auditable.accion(), auditable.entidad(),
                    buscarId(punto.getArgs(), resultado), "Registrado por AuditoriaAspect");
            return resultado;
        } catch (RuntimeException e) {
            auditoriaService.registrarFalloOperacion(operador, auditable.modulo(), auditable.accion(),
                    auditable.entidad(), idEntidad, e);
            throw e;
        }
    }

    // El usuario que hace la operación es el primer parámetro de tipo Usuario.
    private Usuario buscarOperador(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof Usuario usuario) {
                return usuario;
            }
        }
        return null;
    }

    // El ID sale del registro devuelto (al crear) o del primer parámetro Long (al editar o eliminar).
    private Long buscarId(Object[] args, Object resultado) {
        if (resultado instanceof Cita cita) {
            return cita.getIdCita();
        }
        for (Object arg : args) {
            if (arg instanceof Long id) {
                return id;
            }
        }
        return null;
    }
}
