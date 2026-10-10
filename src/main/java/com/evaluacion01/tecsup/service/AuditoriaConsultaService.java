package com.evaluacion01.tecsup.service;

import com.evaluacion01.tecsup.audit.ModuloAuditoria;
import com.evaluacion01.tecsup.dto.AuditoriaFiltroDto;
import com.evaluacion01.tecsup.entity.AuditoriaLog;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.AuditoriaLogRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuditoriaConsultaService {

    private final AuditoriaLogRepository repository;
    private final AutorizacionService autorizacion;
    private final AuditoriaService auditoria;
    private final Validator validator;

    @Transactional(readOnly = true)
    public Page<AuditoriaLog> consultar(Usuario operador, AuditoriaFiltroDto filtro) {
        if (!autorizacion.esAdministrador(operador)) {
            auditoria.registrarFallo(operador, null, ModuloAuditoria.AUDITORIA, "CONSULTAR_AUDITORIA",
                    null, null, "SOLO_ADMINISTRADOR", true);
            throw new AccessDeniedException("Solo un Administrador puede consultar la auditoría.");
        }
        if (filtro == null || !validator.validate(filtro).isEmpty()) {
            throw new IllegalArgumentException("Los filtros de auditoría no son válidos.");
        }
        PageRequest pagina = PageRequest.of(filtro.getPagina(), filtro.getTamano(),
                Sort.by(Sort.Direction.DESC, "fechaHora", "idAuditoria"));
        return repository.findAll(especificacion(filtro), pagina);
    }

    private Specification<AuditoriaLog> especificacion(AuditoriaFiltroDto filtro) {
        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (filtro.getModulo() != null) condiciones.add(cb.equal(root.get("modulo"), filtro.getModulo()));
            if (filtro.getResultado() != null) condiciones.add(cb.equal(root.get("resultado"), filtro.getResultado()));
            if (filtro.getUsuario() != null && !filtro.getUsuario().isBlank()) {
                String texto = filtro.getUsuario().trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                condiciones.add(cb.like(cb.lower(root.get("usuarioEjecutor")), "%" + texto + "%", '\\'));
            }
            if (filtro.getDesde() != null) {
                condiciones.add(cb.greaterThanOrEqualTo(root.get("fechaHora"), filtro.getDesde().atStartOfDay()));
            }
            if (filtro.getHasta() != null) {
                condiciones.add(cb.lessThan(root.get("fechaHora"), filtro.getHasta().plusDays(1).atStartOfDay()));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }
}
