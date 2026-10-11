package com.evaluacion01.tecsup.service;

import com.evaluacion01.tecsup.audit.ModuloAuditoria;
import com.evaluacion01.tecsup.dto.PacienteFormulario;
import com.evaluacion01.tecsup.entity.Paciente;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.PacienteRepository;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PacienteService {

    private final PacienteRepository pacientes;
    private final AutorizacionService autorizacion;
    private final AuditoriaService auditoria;
    private final Validator validator;

    @Transactional(readOnly = true)
    public List<Paciente> buscar(Usuario operador, String texto) {
        try {
            exigir(operador, "VER");
            if (texto != null && texto.length() > 100) throw new IllegalArgumentException("La búsqueda admite hasta 100 caracteres.");
            String patron = texto == null || texto.isBlank() ? null : "%" + texto.trim().toLowerCase(Locale.ROOT) + "%";
            return pacientes.buscar(patron, PageRequest.of(0, 100));
        } catch (RuntimeException e) {
            auditoria.registrarFalloOperacion(operador, ModuloAuditoria.PACIENTES, "CONSULTAR_PACIENTES", "Paciente", null, e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Paciente obtener(Usuario operador, Long id) {
        try {
            exigir(operador, "VER");
            return encontrar(id);
        } catch (RuntimeException e) {
            auditoria.registrarFalloOperacion(operador, ModuloAuditoria.PACIENTES, "CONSULTAR_PACIENTE", "Paciente", id, e);
            throw e;
        }
    }

    @Transactional
    public Paciente guardar(Usuario operador, PacienteFormulario formulario) {
        boolean nuevo = formulario.getIdPaciente() == null;
        String accion = nuevo ? "CREAR_PACIENTE" : "EDITAR_PACIENTE";
        try {
            exigir(operador, "VER");
            exigir(operador, nuevo ? "CREAR" : "EDITAR");
            formulario.setNombres(limpiar(formulario.getNombres()));
            formulario.setApellidos(limpiar(formulario.getApellidos()));
            formulario.setDni(limpiar(formulario.getDni()));
            formulario.setTelefono(limpiar(formulario.getTelefono()));
            if (!validator.validate(formulario).isEmpty()) throw new IllegalArgumentException("Datos del paciente inválidos. Revisa los campos obligatorios y sus formatos.");
            boolean duplicado = nuevo ? pacientes.existsByDni(formulario.getDni())
                    : pacientes.existsByDniAndIdPacienteNot(formulario.getDni(), formulario.getIdPaciente());
            if (duplicado) throw new IllegalArgumentException("Ya existe un paciente con ese DNI.");
            Paciente paciente = nuevo ? new Paciente() : encontrar(formulario.getIdPaciente());
            paciente.setNombres(formulario.getNombres());
            paciente.setApellidos(formulario.getApellidos());
            paciente.setDni(formulario.getDni());
            paciente.setFechaNacimiento(formulario.getFechaNacimiento());
            paciente.setTelefono(formulario.getTelefono());
            pacientes.saveAndFlush(paciente);
            // No copiar DNI, nombres, teléfono ni información médica a la bitácora.
            auditoria.registrarExito(operador, ModuloAuditoria.PACIENTES, accion, "Paciente", paciente.getIdPaciente(),
                    nuevo ? "Registro demográfico creado" : "Registro demográfico actualizado");
            return paciente;
        } catch (RuntimeException e) {
            auditoria.registrarFalloOperacion(operador, ModuloAuditoria.PACIENTES, accion, "Paciente", formulario.getIdPaciente(), e);
            throw e;
        }
    }

    private void exigir(Usuario operador, String permiso) {
        if (!autorizacion.tienePermiso(operador, "pacientes", permiso)) throw new AccessDeniedException("No tienes permiso para esta operación de pacientes.");
    }

    private Paciente encontrar(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("El identificador del paciente no es válido.");
        return pacientes.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente no encontrado."));
    }

    private String limpiar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
