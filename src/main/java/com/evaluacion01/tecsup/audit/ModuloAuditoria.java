package com.evaluacion01.tecsup.audit;

public enum ModuloAuditoria {
    AUTENTICACION, USUARIOS, ROLES, PERMISOS;

    public static ModuloAuditoria deRuta(String ruta) {
        if (ruta.equals("/usuarios") || ruta.startsWith("/usuarios/")) return USUARIOS;
        if (ruta.startsWith("/roles/") && ruta.endsWith("/permisos")) return PERMISOS;
        if (ruta.equals("/roles") || ruta.startsWith("/roles/")) return ROLES;
        return AUTENTICACION;
    }
}
