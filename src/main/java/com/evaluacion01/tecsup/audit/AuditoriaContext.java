package com.evaluacion01.tecsup.audit;

// Solo metadatos de la petición: nunca su cuerpo, parámetros ni cabeceras de autenticación.
public final class AuditoriaContext {

    private static final ThreadLocal<DatosPeticion> DATOS = new ThreadLocal<>();

    private AuditoriaContext() {
    }

    public record DatosPeticion(String ip, String metodo, String ruta) {
    }

    public static void establecer(DatosPeticion datos) {
        DATOS.set(datos);
    }

    public static DatosPeticion obtener() {
        DatosPeticion datos = DATOS.get();
        return datos == null ? new DatosPeticion(null, null, null) : datos;
    }

    public static void limpiar() {
        DATOS.remove();
    }
}
