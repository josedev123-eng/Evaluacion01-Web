package com.evaluacion01.tecsup.entity;

import com.evaluacion01.tecsup.audit.ModuloAuditoria;
import com.evaluacion01.tecsup.audit.ResultadoAuditoria;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "auditoria_logs", indexes = {
        @Index(name = "idx_auditoria_fecha", columnList = "fecha_hora,id_auditoria"),
        @Index(name = "idx_auditoria_modulo_fecha", columnList = "modulo,fecha_hora"),
        @Index(name = "idx_auditoria_usuario_fecha", columnList = "usuario_ejecutor,fecha_hora")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditoriaLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_auditoria")
    private Long idAuditoria;

    @Column(name = "fecha_hora", nullable = false, updatable = false)
    private LocalDateTime fechaHora;

    // Una instantánea, sin FK: el historial permanece aunque la cuenta cambie o se elimine.
    @Column(name = "id_usuario_ejecutor", updatable = false)
    private Long idUsuarioEjecutor;

    @Column(name = "usuario_ejecutor", nullable = false, length = 100, updatable = false)
    private String usuarioEjecutor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private ModuloAuditoria modulo;

    @Column(nullable = false, length = 50, updatable = false)
    private String accion;

    @Column(length = 50, updatable = false)
    private String entidad;

    @Column(name = "id_entidad", updatable = false)
    private Long idEntidad;

    @Column(length = 1000, updatable = false)
    private String detalle;

    @Column(length = 45, updatable = false)
    private String ip;

    @Column(name = "metodo_http", length = 10, updatable = false)
    private String metodoHttp;

    @Column(length = 255, updatable = false)
    private String ruta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private ResultadoAuditoria resultado;

    public AuditoriaLog(LocalDateTime fechaHora, Long idUsuarioEjecutor, String usuarioEjecutor,
                        ModuloAuditoria modulo, String accion, String entidad, Long idEntidad, String detalle,
                        String ip, String metodoHttp, String ruta, ResultadoAuditoria resultado) {
        this.fechaHora = fechaHora;
        this.idUsuarioEjecutor = idUsuarioEjecutor;
        this.usuarioEjecutor = usuarioEjecutor;
        this.modulo = modulo;
        this.accion = accion;
        this.entidad = entidad;
        this.idEntidad = idEntidad;
        this.detalle = detalle;
        this.ip = ip;
        this.metodoHttp = metodoHttp;
        this.ruta = ruta;
        this.resultado = resultado;
    }
}
