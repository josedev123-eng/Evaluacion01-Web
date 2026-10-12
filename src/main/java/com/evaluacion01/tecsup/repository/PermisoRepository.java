package com.evaluacion01.tecsup.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import com.evaluacion01.tecsup.entity.Permiso;
import java.util.List;

public interface PermisoRepository extends JpaRepository<Permiso, Integer> {
    @EntityGraph(attributePaths = "roles")
    List<Permiso> findAllByOrderByModuloAscNombreAsc();
}
