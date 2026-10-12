package com.evaluacion01.tecsup.repository;

import com.evaluacion01.tecsup.entity.AuditoriaLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditoriaLogRepository extends JpaRepository<AuditoriaLog, Long>,
        JpaSpecificationExecutor<AuditoriaLog> {
}
