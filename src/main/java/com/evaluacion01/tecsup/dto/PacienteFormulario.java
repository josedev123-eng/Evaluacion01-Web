package com.evaluacion01.tecsup.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class PacienteFormulario {

    @Positive(message = "El identificador del paciente no es válido.")
    private Long idPaciente;

    @NotBlank(message = "Los nombres son obligatorios.")
    @Size(max = 100, message = "Los nombres admiten hasta 100 caracteres.")
    private String nombres;

    @NotBlank(message = "Los apellidos son obligatorios.")
    @Size(max = 100, message = "Los apellidos admiten hasta 100 caracteres.")
    private String apellidos;

    @NotBlank(message = "El DNI es obligatorio.")
    @Pattern(regexp = "[0-9]{8}", message = "El DNI debe tener 8 dígitos.")
    private String dni;

    @NotNull(message = "La fecha de nacimiento es obligatoria.")
    @PastOrPresent(message = "La fecha de nacimiento no puede ser futura.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaNacimiento;

    @Size(max = 20, message = "El teléfono admite hasta 20 caracteres.")
    @Pattern(regexp = "|\\+?[0-9 ]{6,20}", message = "El teléfono solo admite dígitos, espacios y un + inicial.")
    private String telefono;
}
