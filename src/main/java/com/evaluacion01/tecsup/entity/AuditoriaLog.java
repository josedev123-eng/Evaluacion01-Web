package com.evaluacion01.tecsup.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// RF-AUD-01: registro de operaciones críticas (autenticación, usuarios, roles y permisos).
@Entity
@Table(name = "auditoria_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditoriaLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_auditoria")
    private Long idAuditoria;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    // Usuario que ejecutó la operación. En intentos de login fallidos guarda el identificador intentado.
    @Column(name = "usuario_ejecutor", length = 50)
    private String usuarioEjecutor;

    @Column(nullable = false, length = 50)
    private String modulo;

    @Column(nullable = false, length = 50)
    private String accion;

    @Column(length = 50)
    private String entidad;

    @Column(name = "id_entidad")
    private Long idEntidad;

    @Column(length = 1000)
    private String detalle;

    @Column(length = 45)
    private String ip;

    // EXITO / FALLO
    @Column(nullable = false, length = 10)
    private String resultado;
}
