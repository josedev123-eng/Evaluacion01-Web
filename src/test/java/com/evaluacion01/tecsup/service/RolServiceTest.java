package com.evaluacion01.tecsup.service;

import com.evaluacion01.tecsup.entity.Permiso;
import com.evaluacion01.tecsup.entity.Rol;
import com.evaluacion01.tecsup.repository.PermisoRepository;
import com.evaluacion01.tecsup.repository.RolRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RolServiceTest {

    @Mock
    private RolRepository rolRepository;

    @Mock
    private PermisoRepository permisoRepository;

    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private RolService rolService;

    private Rol rolConPermisos(Integer id, String nombre) {
        Rol rol = new Rol();
        rol.setIdRol(id);
        rol.setNombre(nombre);
        rol.setDescripcion("Rol de prueba");
        rol.setArea("Administración");
        return rol;
    }

    private Permiso permiso(Integer id, String nombre, String modulo) {
        Permiso permiso = new Permiso();
        permiso.setIdPermiso(id);
        permiso.setNombre(nombre);
        permiso.setModulo(modulo);
        return permiso;
    }

    @Test
    void guardarRolNuevoDebeRegistrarLaAlta() {
        Rol nuevo = rolConPermisos(null, "Jefe de área");
        when(rolRepository.findByNombreIgnoreCase("Jefe de área")).thenReturn(Optional.empty());
        when(rolRepository.save(any(Rol.class))).thenAnswer(invocation -> {
            Rol guardado = invocation.getArgument(0);
            guardado.setIdRol(9);
            return guardado;
        });

        rolService.guardar(nuevo);

        verify(auditoriaService).registrar(eq(AuditoriaService.MODULO_ROLES), eq("CREAR_ROL"),
                eq("Rol"), eq(9L), contains("Rol creado: Jefe de área"));
    }

    @Test
    void guardarRolExistenteDebeDetallarLosCambios() {
        Rol existente = rolConPermisos(3, "Coordinador administrativo");
        existente.setArea("Administración");
        when(rolRepository.findByNombreIgnoreCase("Coordinador")).thenReturn(Optional.empty());
        when(rolRepository.findById(3)).thenReturn(Optional.of(existente));
        when(rolRepository.save(any(Rol.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Rol modificado = rolConPermisos(3, "Coordinador");
        modificado.setArea("Medicina");

        rolService.guardar(modificado);

        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(auditoriaService).registrar(eq(AuditoriaService.MODULO_ROLES), eq("EDITAR_ROL"),
                eq("Rol"), eq(3L), detalle.capture());
        assertThat(detalle.getValue())
                .contains("nombre: 'Coordinador administrativo' -> 'Coordinador'")
                .contains("area: 'Administración' -> 'Medicina'");
    }

    @Test
    void asignarPermisosDebeRegistrarAltasYBajas() {
        Rol rol = rolConPermisos(3, "Coordinador administrativo");
        rol.setPermisos(Set.of(permiso(1, "VER", "usuarios")));

        Permiso nuevoPermiso = permiso(2, "EDITAR", "usuarios");
        when(rolRepository.findById(3)).thenReturn(Optional.of(rol));
        when(permisoRepository.findAllById(List.of(2))).thenReturn(List.of(nuevoPermiso));
        when(rolRepository.save(any(Rol.class))).thenAnswer(invocation -> invocation.getArgument(0));

        rolService.asignarPermisos(3, List.of(2));

        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(auditoriaService).registrar(eq(AuditoriaService.MODULO_PERMISOS), eq("CAMBIAR_PERMISOS_ROL"),
                eq("Rol"), eq(3L), detalle.capture());
        assertThat(detalle.getValue())
                .contains("Permisos agregados: usuarios.EDITAR")
                .contains("Permisos quitados: usuarios.VER");
    }

    @Test
    void asignarTodosLosPermisosVaciosDebeIndicarQueNoHuboCambios() {
        Rol rol = rolConPermisos(3, "Coordinador administrativo");
        when(rolRepository.findById(3)).thenReturn(Optional.of(rol));
        when(rolRepository.save(any(Rol.class))).thenAnswer(invocation -> invocation.getArgument(0));

        rolService.asignarPermisos(3, null);

        verify(auditoriaService).registrar(eq(AuditoriaService.MODULO_PERMISOS), eq("CAMBIAR_PERMISOS_ROL"),
                eq("Rol"), eq(3L), contains("Sin cambios en los permisos"));
    }

    @Test
    void rolSinNombreNoDebeGenerarRegistroDeAuditoria() {
        Rol sinNombre = rolConPermisos(null, "   ");

        assertThatThrownBy(() -> rolService.guardar(sinNombre))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El nombre del rol es obligatorio");

        verify(auditoriaService, org.mockito.Mockito.never())
                .registrar(any(String.class), any(String.class), any(String.class), any(String.class),
                        org.mockito.ArgumentMatchers.anyLong(), any(String.class), any(String.class));
    }
}
