package com.evaluacion01.tecsup.service;

import com.evaluacion01.tecsup.audit.ModuloAuditoria;
import com.evaluacion01.tecsup.audit.ZonaHoraria;
import com.evaluacion01.tecsup.dto.CitaFormulario;
import com.evaluacion01.tecsup.entity.Cita;
import com.evaluacion01.tecsup.entity.EstadoCita;
import com.evaluacion01.tecsup.entity.Paciente;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.CitaRepository;
import com.evaluacion01.tecsup.repository.PacienteRepository;
import com.evaluacion01.tecsup.repository.UsuarioRepository;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

// Citas: Recepción las programa; los médicos ven solo las suyas. No se eliminan,
// cambian de estado (ATENDIDA o CANCELADA) y cada operación queda en la auditoría.
@Service
@RequiredArgsConstructor
public class CitaService {

    private final CitaRepository citas;
    private final PacienteRepository pacientes;
    private final UsuarioRepository usuarios;
    private final AutorizacionService autorizacion;
    private final AuditoriaService auditoria;
    private final Clock auditoriaClock;
    private final Validator validator;

    @Transactional(readOnly = true)
    public List<Cita> consultar(Usuario operador) {
        try {
            exigir(operador, "VER");
            // El filtro por profesional lo decide el servidor, nunca un parámetro de la URL.
            Long idMedico = veTodas(operador) ? null : operador.getIdUsuario();
            return citas.consultar(idMedico, PageRequest.of(0, 100));
        } catch (RuntimeException e) {
            auditoria.registrarFalloOperacion(operador, ModuloAuditoria.CITAS, "CONSULTAR_CITAS", "Cita", null, e);
            throw e;
        }
    }

    // Administrador y quien programa o gestiona citas ven la agenda completa.
    public boolean veTodas(Usuario operador) {
        return autorizacion.esAdministrador(operador)
                || autorizacion.tienePermiso(operador, "citas", "CREAR")
                || autorizacion.tienePermiso(operador, "citas", "EDITAR");
    }

    @Transactional(readOnly = true)
    public List<Usuario> medicosDisponibles(Usuario operador) {
        exigir(operador, "CREAR");
        return usuarios.activosConPermiso("atenciones", "crear");
    }

    @Transactional
    public Cita registrar(Usuario operador, CitaFormulario formulario) {
        try {
            exigir(operador, "CREAR");
            if (!autorizacion.tienePermiso(operador, "pacientes", "VER")) {
                throw new AccessDeniedException("No tienes permiso para consultar pacientes.");
            }
            formulario.setMotivo(formulario.getMotivo() == null ? null : formulario.getMotivo().trim());
            if (!validator.validate(formulario).isEmpty()) {
                throw new IllegalArgumentException("Datos de la cita inválidos. Revisa paciente, médico, fecha y motivo.");
            }
            Paciente paciente = pacientes.findById(formulario.getIdPaciente())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente no encontrado."));
            Usuario medico = usuarios.activosConPermiso("atenciones", "crear").stream()
                    .filter(u -> u.getIdUsuario().equals(formulario.getIdMedico())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("El médico seleccionado no está disponible."));
            // La fecha se escribe en hora de Lima y se guarda como instante (UTC).
            Instant fecha = formulario.getFechaHora().atZone(ZonaHoraria.LIMA).toInstant();
            if (!fecha.isAfter(Instant.now(auditoriaClock))) {
                throw new IllegalArgumentException("La cita debe programarse en una fecha y hora futuras.");
            }
            if (citas.existsByMedico_IdUsuarioAndFechaHoraAndEstado(medico.getIdUsuario(), fecha, EstadoCita.PROGRAMADA)) {
                throw new IllegalArgumentException("El médico ya tiene una cita programada a esa hora.");
            }
            Usuario registradora = usuarios.findById(operador.getIdUsuario())
                    .filter(u -> Boolean.TRUE.equals(u.getEstado()))
                    .orElseThrow(() -> new AccessDeniedException("La cuenta ya no está activa."));
            Cita cita = new Cita(paciente, medico, registradora, fecha, formulario.getMotivo());
            citas.saveAndFlush(cita);
            // Sin nombres, DNI ni motivo en la bitácora: solo identificadores.
            auditoria.registrarExito(registradora, ModuloAuditoria.CITAS, "REGISTRAR_CITA", "Cita", cita.getIdCita(),
                    "Paciente #" + paciente.getIdPaciente() + "; profesional #" + medico.getIdUsuario());
            return cita;
        } catch (RuntimeException e) {
            auditoria.registrarFalloOperacion(operador, ModuloAuditoria.CITAS, "REGISTRAR_CITA", "Cita", null, e);
            throw e;
        }
    }

    @Transactional
    public Cita cambiarEstado(Usuario operador, Long idCita, EstadoCita nuevo) {
        String accion = nuevo == EstadoCita.CANCELADA ? "CANCELAR_CITA" : "ATENDER_CITA";
        try {
            exigir(operador, "EDITAR");
            if (nuevo == null || nuevo == EstadoCita.PROGRAMADA) {
                throw new IllegalArgumentException("El estado solicitado no es válido.");
            }
            if (idCita == null || idCita <= 0) {
                throw new IllegalArgumentException("El identificador de la cita no es válido.");
            }
            Cita cita = citas.findById(idCita)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cita no encontrada."));
            if (cita.getEstado() != EstadoCita.PROGRAMADA) {
                throw new IllegalArgumentException("Solo se puede modificar una cita programada.");
            }
            EstadoCita anterior = cita.getEstado();
            cita.cambiarEstado(nuevo);
            citas.saveAndFlush(cita);
            auditoria.registrarExito(operador, ModuloAuditoria.CITAS, accion, "Cita", idCita,
                    "Estado: " + anterior + " -> " + nuevo);
            return cita;
        } catch (RuntimeException e) {
            auditoria.registrarFalloOperacion(operador, ModuloAuditoria.CITAS, accion, "Cita", idCita, e);
            throw e;
        }
    }

    private void exigir(Usuario operador, String permiso) {
        if (!autorizacion.tienePermiso(operador, "citas", permiso)) {
            throw new AccessDeniedException("No tienes permiso para esta operación de citas.");
        }
    }
}
