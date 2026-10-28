package com.evaluacion01.tecsup.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Marca un método de servicio para que AuditoriaAspect registre la operación
// (usuario, fecha y hora, acción, entidad e ID) sin escribir la llamada a mano.
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    ModuloAuditoria modulo();

    String accion();

    String entidad();
}
