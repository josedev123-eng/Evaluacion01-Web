package com.evaluacion01.tecsup.repository;

import com.evaluacion01.tecsup.entity.Atencion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AtencionRepository extends JpaRepository<Atencion, Long> {

    @EntityGraph(attributePaths = {"paciente", "medico"})
    @Query("SELECT a FROM Atencion a WHERE (:idPaciente IS NULL OR a.paciente.idPaciente = :idPaciente) AND (:idMedico IS NULL OR a.medico.idUsuario = :idMedico) ORDER BY a.fechaHora DESC, a.idAtencion DESC")
    List<Atencion> consultar(@Param("idPaciente") Long idPaciente, @Param("idMedico") Long idMedico, Pageable limite);
}
