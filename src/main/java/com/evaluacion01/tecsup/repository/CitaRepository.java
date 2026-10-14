package com.evaluacion01.tecsup.repository;

import com.evaluacion01.tecsup.entity.Cita;
import com.evaluacion01.tecsup.entity.EstadoCita;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CitaRepository extends JpaRepository<Cita, Long> {

    @EntityGraph(attributePaths = {"paciente", "medico"})
    @Query("SELECT c FROM Cita c WHERE (:idMedico IS NULL OR c.medico.idUsuario = :idMedico) ORDER BY c.fechaHora DESC, c.idCita DESC")
    List<Cita> consultar(@Param("idMedico") Long idMedico, Pageable limite);

    boolean existsByMedico_IdUsuarioAndFechaHoraAndEstado(Long idMedico, Instant fechaHora, EstadoCita estado);
}
