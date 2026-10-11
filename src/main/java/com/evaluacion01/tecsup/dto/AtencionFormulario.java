package com.evaluacion01.tecsup.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class AtencionFormulario {

    @NotNull(message = "Selecciona un paciente.")
    @Positive(message = "El identificador del paciente no es válido.")
    private Long idPaciente;

    @NotBlank(message = "El motivo de consulta es obligatorio.")
    @Size(max = 250, message = "El motivo admite hasta 250 caracteres.")
    private String motivo;

    @NotBlank(message = "El diagnóstico es obligatorio.")
    @Size(max = 2000, message = "El diagnóstico admite hasta 2000 caracteres.")
    private String diagnostico;

    @Size(max = 2000, message = "Las indicaciones admiten hasta 2000 caracteres.")
    private String indicaciones;
}
