package com.evaluacion01.tecsup.entity;

import com.evaluacion01.tecsup.audit.ZonaHoraria;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "atenciones", indexes = {
        @Index(name = "idx_atencion_paciente_fecha", columnList = "id_paciente,fecha_hora,id_atencion"),
        @Index(name = "idx_atencion_medico_fecha", columnList = "id_medico,fecha_hora,id_atencion")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Atencion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_atencion")
    private Long idAtencion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_paciente", nullable = false, updatable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_medico", nullable = false, updatable = false)
    private Usuario medico;

    @Column(name = "fecha_hora", nullable = false, updatable = false)
    private Instant fechaHora;

    @Column(nullable = false, length = 250, updatable = false)
    private String motivo;

    @Column(nullable = false, length = 2000, updatable = false)
    private String diagnostico;

    @Column(length = 2000, updatable = false)
    private String indicaciones;

    public Atencion(Paciente paciente, Usuario medico, Instant fechaHora,
                    String motivo, String diagnostico, String indicaciones) {
        this.paciente = paciente;
        this.medico = medico;
        this.fechaHora = fechaHora;
        this.motivo = motivo;
        this.diagnostico = diagnostico;
        this.indicaciones = indicaciones;
    }

    // Instant conserva el momento real al enlazar JDBC, sin reinterpretarlo en
    // la zona local del equipo. La vista presenta explícitamente UTC.
    public LocalDateTime getFechaHoraUtc() {
        return LocalDateTime.ofInstant(fechaHora, ZoneOffset.UTC);
    }

    public LocalDateTime getFechaHoraLocal() {
        return LocalDateTime.ofInstant(fechaHora, ZonaHoraria.LIMA);
    }
}
