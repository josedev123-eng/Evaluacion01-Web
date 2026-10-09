package com.evaluacion01.tecsup.repository;

import com.evaluacion01.tecsup.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByUsuario(String usuario);
    Optional<Usuario> findByCorreo(String correo);
    Optional<Usuario> findByUsuarioOrCorreo(String usuario, String correo);
    Optional<Usuario> findByResetToken(String resetToken);
    boolean existsByUsuario(String usuario);
    boolean existsByCorreo(String correo);
    long countByEstadoTrueAndRol_NombreIgnoreCase(String nombreRol);

    // RF-USR-05: búsqueda por texto (nombres, apellidos, DNI, correo o usuario) y filtros
    // opcionales por área, rol (principal o adicional) y estado. Un parámetro null no filtra.
    @Query("""
            SELECT u FROM Usuario u
            WHERE (:texto IS NULL
                   OR LOWER(u.nombres) LIKE :texto
                   OR LOWER(u.apellidos) LIKE :texto
                   OR LOWER(CONCAT(u.nombres, ' ', u.apellidos)) LIKE :texto
                   OR LOWER(u.dni) LIKE :texto
                   OR LOWER(u.correo) LIKE :texto
                   OR LOWER(u.usuario) LIKE :texto)
              AND (:area IS NULL OR u.area = :area)
              AND (:estado IS NULL OR u.estado = :estado)
              AND (:idRol IS NULL
                   OR u.rol.idRol = :idRol
                   OR EXISTS (SELECT 1 FROM Usuario u2 JOIN u2.roles r
                              WHERE u2.idUsuario = u.idUsuario AND r.idRol = :idRol))
            ORDER BY u.apellidos, u.nombres
            """)
    List<Usuario> buscar(@Param("texto") String texto,
                         @Param("area") String area,
                         @Param("idRol") Integer idRol,
                         @Param("estado") Boolean estado);
}
