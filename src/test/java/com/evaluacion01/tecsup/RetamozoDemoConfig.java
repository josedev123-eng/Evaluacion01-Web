package com.evaluacion01.tecsup;

import com.evaluacion01.tecsup.entity.Permiso;
import com.evaluacion01.tecsup.entity.Rol;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.PermisoRepository;
import com.evaluacion01.tecsup.repository.RolRepository;
import com.evaluacion01.tecsup.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

// Solo está en el classpath de pruebas y solo actúa sobre H2 en memoria.
@Configuration(proxyBeanMethods = false)
@Profile("test & demo")
public class RetamozoDemoConfig {

    @Bean
    CommandLineRunner datosDemoRetamozo(DataSource dataSource, UsuarioRepository usuarios, RolRepository roles,
                                       PermisoRepository permisos, PasswordEncoder encoder) {
        return args -> {
            try (var conexion = dataSource.getConnection()) {
                if (!conexion.getMetaData().getURL().startsWith("jdbc:h2:mem:") || usuarios.count() != 0) {
                    throw new IllegalStateException("La demostración requiere una base H2 en memoria vacía.");
                }
            }
            Permiso ver = permiso(permisos, "VER");
            Permiso crear = permiso(permisos, "CREAR");
            Permiso editar = permiso(permisos, "EDITAR");
            Rol admin = rol(roles, "Administrador", "Administración", true);
            Rol medico = rol(roles, "Médico", "Medicina", true, ver);
            Rol recepcion = rol(roles, "Recepcionista", "Recepción", true, ver);
            Rol coordinador = rol(roles, "Coordinador médico", "Medicina", true, ver, crear, editar);
            Rol apoyo = rol(roles, "Apoyo médico", "Medicina", false, ver);
            String clave = UUID.randomUUID().toString();
            usuario(usuarios, encoder, "demoAdmin", "Administración", clave, admin);
            usuario(usuarios, encoder, "demoMedico", "Medicina", clave, medico, apoyo);
            usuario(usuarios, encoder, "demoRecepcion", "Recepción", clave, recepcion);
            usuario(usuarios, encoder, "demoEditor", "Medicina", clave, coordinador, medico);
            System.out.println("[DEMO H2] Usuarios: demoAdmin, demoMedico, demoRecepcion, demoEditor. Clave temporal: " + clave);
        };
    }

    private Permiso permiso(PermisoRepository repository, String nombre) {
        Permiso permiso = new Permiso();
        permiso.setNombre(nombre);
        permiso.setModulo("usuarios");
        return repository.saveAndFlush(permiso);
    }

    private Rol rol(RolRepository repository, String nombre, String area, boolean activo, Permiso... permisos) {
        Rol rol = new Rol();
        rol.setNombre(nombre);
        rol.setArea(area);
        rol.setEstado(activo);
        rol.setPermisos(new LinkedHashSet<>(List.of(permisos)));
        return repository.saveAndFlush(rol);
    }

    private void usuario(UsuarioRepository repository, PasswordEncoder encoder, String nombre, String area,
                         String clave, Rol principal, Rol... adicionales) {
        Usuario usuario = new Usuario();
        usuario.setUsuario(nombre);
        usuario.setCorreo(nombre.toLowerCase() + "@pruebas.local");
        usuario.setNombres(nombre);
        usuario.setApellidos("Demostración");
        usuario.setArea(area);
        usuario.setRol(principal);
        usuario.setRoles(new LinkedHashSet<>(List.of(adicionales)));
        usuario.getRoles().add(principal);
        usuario.setContrasena(encoder.encode(clave));
        repository.saveAndFlush(usuario);
    }
}
