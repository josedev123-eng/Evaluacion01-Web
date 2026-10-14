package com.evaluacion01.tecsup.entity;

import com.evaluacion01.tecsup.audit.ZonaHoraria;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "citas", indexes = {
        @Index(name = "idx_cita_fecha", columnList = "fecha_hora,id_cita"),
        @Index(name = "idx_cita_medico_fecha", columnList = "id_medico,fecha_hora")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cita")
    private Long idCita;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_paciente", nullable = false, updatable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_medico", nullable = false, updatable = false)
    private Usuario medico;

    // Quién la programó (normalmente Recepción); el servidor lo toma de la sesión.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_registrado_por", nullable = false, updatable = false)
    private Usuario registradaPor;

    @Column(name = "fecha_hora", nullable = false, updatable = false)
    private Instant fechaHora;

    @Column(nullable = false, length = 250, updatable = false)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 15)
    private EstadoCita estado = EstadoCita.PROGRAMADA;

    public Cita(Paciente paciente, Usuario medico, Usuario registradaPor, Instant fechaHora, String motivo) {
        this.paciente = paciente;
        this.medico = medico;
        this.registradaPor = registradaPor;
        this.fechaHora = fechaHora;
        this.motivo = motivo;
    }

    public void cambiarEstado(EstadoCita nuevo) {
        this.estado = nuevo;
    }

    public LocalDateTime getFechaHoraLocal() {
        return LocalDateTime.ofInstant(fechaHora, ZonaHoraria.LIMA);
    }
}
