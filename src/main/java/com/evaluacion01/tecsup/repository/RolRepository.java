package com.evaluacion01.tecsup.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.evaluacion01.tecsup.entity.Rol;

public interface RolRepository extends JpaRepository<Rol, Integer> {

    Optional<Rol> findByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCase(String nombre);
}
