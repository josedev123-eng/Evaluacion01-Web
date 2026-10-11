package com.evaluacion01.tecsup.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "permisos", uniqueConstraints = @UniqueConstraint(name = "uq_permiso_modulo", columnNames = {"nombre", "modulo"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Permiso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_permiso")
    private Integer idPermiso;

    @Column(name = "nombre", nullable = false, length = 50)
    private String nombre;

    @Column(name = "modulo", nullable = false, length = 50)
    private String modulo;

    // P1 de Retamozo: lado inverso; Rol.permisos sigue siendo quien escribe rol_permisos.
    // Sin cascadas hacia roles compartidos y sin crear otra tabla de asociaciones.
    @ManyToMany(mappedBy = "permisos", fetch = FetchType.LAZY)
    @OrderBy("nombre ASC, idRol ASC")
    private Set<Rol> roles = new LinkedHashSet<>();
}
