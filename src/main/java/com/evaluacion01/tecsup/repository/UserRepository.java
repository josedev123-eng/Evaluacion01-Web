package com.evaluacion01.tecsup.repository;

import com.evaluacion01.tecsup.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByCorreo(String correo);
    Optional<User> findByUsuario(String usuario);
    Optional<User> findByUsuarioOrCorreo(String usuario, String correo);
    Optional<User> findByResetToken(String resetToken);
}