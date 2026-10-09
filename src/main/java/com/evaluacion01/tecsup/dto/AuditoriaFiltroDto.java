package com.evaluacion01.tecsup.dto;

import com.evaluacion01.tecsup.audit.ModuloAuditoria;
import com.evaluacion01.tecsup.audit.ResultadoAuditoria;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class AuditoriaFiltroDto {

    private ModuloAuditoria modulo;
    private ResultadoAuditoria resultado;

    @Size(max = 100, message = "El filtro de usuario admite hasta 100 caracteres.")
    private String usuario;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate desde;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate hasta;

    @Min(value = 0, message = "La página no puede ser negativa.")
    @Max(value = 100000, message = "El número de página es demasiado alto.")
    private int pagina;

    @Min(value = 1, message = "El tamaño de página debe ser positivo.")
    @Max(value = 100, message = "Se pueden consultar hasta 100 registros por página.")
    private int tamano = 25;

    @AssertTrue(message = "La fecha inicial no puede ser posterior a la final.")
    public boolean isFechasEnOrden() {
        return desde == null || hasta == null || !desde.isAfter(hasta);
    }

    @AssertTrue(message = "Las fechas deben estar entre los años 1900 y 9998.")
    public boolean isFechasValidas() {
        return fechaValida(desde) && fechaValida(hasta);
    }

    private boolean fechaValida(LocalDate fecha) {
        return fecha == null || (fecha.getYear() >= 1900 && fecha.getYear() <= 9998);
    }
}
