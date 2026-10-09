package com.evaluacion01.tecsup.service;

import com.evaluacion01.tecsup.entity.AuditoriaLog;
import com.evaluacion01.tecsup.repository.AuditoriaLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditoriaFallosService {

    private final AuditoriaLogRepository repository;

    // Bean separado para que REQUIRES_NEW se aplique incluso al fallar la operación original.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void guardar(AuditoriaLog evento) {
        repository.saveAndFlush(evento);
    }
}
