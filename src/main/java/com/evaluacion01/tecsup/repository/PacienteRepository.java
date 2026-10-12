package com.evaluacion01.tecsup.repository;

import com.evaluacion01.tecsup.entity.Paciente;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PacienteRepository extends JpaRepository<Paciente, Long> {

    boolean existsByDni(String dni);
    boolean existsByDniAndIdPacienteNot(String dni, Long idPaciente);

    @Query("SELECT p FROM Paciente p WHERE :texto IS NULL OR LOWER(CONCAT(p.nombres, ' ', p.apellidos)) LIKE :texto OR p.dni LIKE :texto ORDER BY p.apellidos, p.nombres, p.idPaciente")
    List<Paciente> buscar(@Param("texto") String texto, Pageable limite);
}
