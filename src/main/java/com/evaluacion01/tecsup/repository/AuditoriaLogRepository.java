package com.evaluacion01.tecsup.repository;

import com.evaluacion01.tecsup.entity.AuditoriaLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditoriaLogRepository extends JpaRepository<AuditoriaLog, Long> {

    List<AuditoriaLog> findAllByOrderByFechaHoraDesc();

    List<AuditoriaLog> findAllByOrderByFechaHoraDesc(Pageable pageable);

    List<AuditoriaLog> findByModuloOrderByFechaHoraDesc(String modulo);

    List<AuditoriaLog> findByUsuarioEjecutorOrderByFechaHoraDesc(String usuarioEjecutor);

    List<AuditoriaLog> findByModuloAndUsuarioEjecutorOrderByFechaHoraDesc(String modulo, String usuarioEjecutor);

    long countByModulo(String modulo);
}
