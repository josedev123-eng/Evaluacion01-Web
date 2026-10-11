package com.evaluacion01.tecsup.service;

import com.evaluacion01.tecsup.audit.ModuloAuditoria;
import com.evaluacion01.tecsup.dto.AtencionFormulario;
import com.evaluacion01.tecsup.entity.Atencion;
import com.evaluacion01.tecsup.entity.Paciente;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.AtencionRepository;
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
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AtencionService {

    private final AtencionRepository atenciones;
    private final PacienteRepository pacientes;
    private final UsuarioRepository usuarios;
    private final AutorizacionService autorizacion;
    private final AuditoriaService auditoria;
    private final Clock auditoriaClock;
    private final Validator validator;

    @Transactional(readOnly = true)
    public List<Atencion> consultar(Usuario operador, Long idPaciente) {
        try {
            exigir(operador, "VER");
            if (idPaciente != null) encontrarPaciente(idPaciente);
            // El filtro de profesional lo decide el servidor, nunca un parámetro de URL.
            Long idMedico = autorizacion.esAdministrador(operador) ? null : operador.getIdUsuario();
            return atenciones.consultar(idPaciente, idMedico, PageRequest.of(0, 100));
        } catch (RuntimeException e) {
            auditoria.registrarFalloOperacion(operador, ModuloAuditoria.ATENCIONES, "CONSULTAR_ATENCIONES", "Paciente", idPaciente, e);
            throw e;
        }
    }

    @Transactional
    public Atencion registrar(Usuario operador, AtencionFormulario formulario) {
        try {
            exigir(operador, "CREAR");
            formulario.setMotivo(limpiar(formulario.getMotivo()));
            formulario.setDiagnostico(limpiar(formulario.getDiagnostico()));
            formulario.setIndicaciones(limpiar(formulario.getIndicaciones()));
            if (!validator.validate(formulario).isEmpty()) throw new IllegalArgumentException("Datos de atención inválidos. Revisa el paciente, motivo y diagnóstico.");
            Paciente paciente = encontrarPaciente(formulario.getIdPaciente());
            Usuario medico = usuarios.findById(operador.getIdUsuario()).filter(u -> Boolean.TRUE.equals(u.getEstado()))
                    .orElseThrow(() -> new AccessDeniedException("La cuenta ya no está activa."));
            Atencion atencion = new Atencion(paciente, medico, LocalDateTime.now(auditoriaClock),
                    formulario.getMotivo(), formulario.getDiagnostico(), formulario.getIndicaciones());
            atenciones.saveAndFlush(atencion);
            auditoria.registrarExito(medico, ModuloAuditoria.ATENCIONES, "REGISTRAR_ATENCION", "Atencion", atencion.getIdAtencion(),
                    "Paciente #" + paciente.getIdPaciente() + "; profesional #" + medico.getIdUsuario());
            return atencion;
        } catch (RuntimeException e) {
            auditoria.registrarFalloOperacion(operador, ModuloAuditoria.ATENCIONES, "REGISTRAR_ATENCION", "Paciente", formulario.getIdPaciente(), e);
            throw e;
        }
    }

    private void exigir(Usuario operador, String permiso) {
        if (!autorizacion.tienePermiso(operador, "pacientes", "VER")
                || !autorizacion.tienePermiso(operador, "atenciones", permiso)) {
            throw new AccessDeniedException("No tienes permiso para esta operación de atenciones.");
        }
    }

    private Paciente encontrarPaciente(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("El identificador del paciente no es válido.");
        return pacientes.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente no encontrado."));
    }

    private String limpiar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
