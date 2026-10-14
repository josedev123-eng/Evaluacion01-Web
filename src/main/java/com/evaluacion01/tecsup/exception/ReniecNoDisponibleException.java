package com.evaluacion01.tecsup.exception;

public class ReniecNoDisponibleException extends RuntimeException {

    public ReniecNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public ReniecNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
