package com.evaluacion01.tecsup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
public class CitaFormulario {

    @NotNull(message = "Selecciona un paciente.")
    @Positive(message = "El identificador del paciente no es válido.")
    private Long idPaciente;

    @NotNull(message = "Selecciona un médico.")
    @Positive(message = "El identificador del médico no es válido.")
    private Long idMedico;

    // Hora de Lima, tal como la escribe quien programa la cita.
    @NotNull(message = "Indica la fecha y hora de la cita.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime fechaHora;

    @NotBlank(message = "El motivo de la cita es obligatorio.")
    @Size(max = 250, message = "El motivo admite hasta 250 caracteres.")
    private String motivo;
}
