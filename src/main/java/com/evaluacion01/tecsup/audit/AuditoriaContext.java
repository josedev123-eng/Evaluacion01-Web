package com.evaluacion01.tecsup.audit;

import java.util.Optional;

// Mantiene en el hilo de la petición el usuario de sesión y la IP, para que
// AuditoriaService pueda registrar quién ejecutó la operación sin necesidad de
// cambiar las firmas de los servicios/controladores existentes.
public final class AuditoriaContext {

    private static final String USUARIO_POR_DEFECTO = "sistema";

    private static final ThreadLocal<String> USUARIO = new ThreadLocal<>();
    private static final ThreadLocal<String> IP = new ThreadLocal<>();

    private AuditoriaContext() {
    }

    public static void set(String usuario, String ip) {
        USUARIO.set(usuario);
        IP.set(ip);
    }

    public static String getUsuario() {
        return Optional.ofNullable(USUARIO.get())
                .filter(valor -> !valor.isBlank())
                .orElse(USUARIO_POR_DEFECTO);
    }

    public static String getIp() {
        return IP.get();
    }

    public static void clear() {
        USUARIO.remove();
        IP.remove();
    }
}
