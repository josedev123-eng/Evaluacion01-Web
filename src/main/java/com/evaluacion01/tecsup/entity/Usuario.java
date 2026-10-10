package com.evaluacion01.tecsup.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "usuarios")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Long idUsuario;

    @Column(nullable = false, length = 100)
    private String nombres;

    @Column(nullable = false, length = 100)
    private String apellidos;

    @Column(unique = true, length = 15)
    private String dni;

    @Column(nullable = false, unique = true, length = 100)
    private String correo;

    @Column(length = 20)
    private String telefono;

    @Column(nullable = false, unique = true, length = 50)
    private String usuario;

    @Column(nullable = false, length = 255)
    private String contrasena;

    @Column(length = 100)
    private String area;

    @Column(nullable = false)
    private Boolean estado = true;

    @Column(name = "fecha_registro", updatable = false)
    private LocalDateTime fechaRegistro = LocalDateTime.now();

    @Column(name = "ultimo_acceso")
    private LocalDateTime ultimoAcceso;

    // Rol principal del usuario (el que se muestra primero y define su Ã¡rea)
    @ManyToOne
    @JoinColumn(name = "id_rol", nullable = false)
    private Rol rol;

    // RF-USR-04: todos los roles asignados al usuario, incluido el principal.
    // EAGER porque el usuario se guarda en la sesiÃ³n HTTP y se usa fuera de una transacciÃ³n.
    @ManyToMany(fetch = FetchType.EAGER, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "usuario_roles",
            joinColumns = @JoinColumn(name = "id_usuario"),
            inverseJoinColumns = @JoinColumn(name = "id_rol")
    )
    @OrderBy("nombre ASC")
    private Set<Rol> roles = new LinkedHashSet<>();

    @Column(name = "reset_token")
    private String resetToken;

    @Column(name = "reset_token_expiry")
    private LocalDateTime resetTokenExpiry;

    // Rol principal primero y luego el resto, sin repetir. Sirve tambiÃ©n para usuarios
    // antiguos que aÃºn no tienen filas en usuario_roles.
    public Set<Rol> getRolesAsignados() {
        Set<Rol> asignados = new LinkedHashSet<>();
        if (rol != null) {
            asignados.add(rol);
        }
        if (roles != null) {
            roles.stream()
                    .filter(Objects::nonNull)
                    .filter(r -> rol == null || !Objects.equals(r.getIdRol(), rol.getIdRol()))
                    .forEach(asignados::add);
        }
        return asignados;
    }
}
