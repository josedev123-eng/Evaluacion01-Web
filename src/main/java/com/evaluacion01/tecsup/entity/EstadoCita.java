package com.evaluacion01.tecsup.entity;

// Una cita no se borra: solo pasa de PROGRAMADA a ATENDIDA o CANCELADA.
public enum EstadoCita {
    PROGRAMADA("Programada"), ATENDIDA("Atendida"), CANCELADA("Cancelada");

    private final String etiqueta;

    EstadoCita(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
