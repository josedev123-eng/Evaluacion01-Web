package com.evaluacion01.tecsup;

import com.evaluacion01.tecsup.entity.Rol;
import com.evaluacion01.tecsup.entity.Usuario;
import com.evaluacion01.tecsup.repository.RolRepository;
import com.evaluacion01.tecsup.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Modo de demostración: sin token devuelve una persona ficticia marcada como simulada.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"reniec.api.token=", "reniec.simulado=true"})
class ReniecSimuladoIntegrationTests {

    @Autowired private MockMvc mvc;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private RolRepository roles;
    @Autowired private PasswordEncoder encoder;

    private Usuario administrador;

    @BeforeEach
    void preparar() {
        Rol rol = new Rol();
        rol.setNombre("Administrador");
        rol.setArea("Administración");
        rol = roles.saveAndFlush(rol);
        Usuario usuario = new Usuario();
        usuario.setNombres("Prueba");
        usuario.setApellidos("Simulado");
        usuario.setUsuario("adminSimulado");
        usuario.setCorreo("adminSimulado@pruebas.local");
        usuario.setContrasena(encoder.encode("claveValida"));
        usuario.setRol(rol);
        usuario.setRoles(new LinkedHashSet<>(Set.of(rol)));
        usuario.setArea("Administración");
        administrador = usuarios.saveAndFlush(usuario);
    }

    @AfterEach
    void limpiar() {
        usuarios.deleteAll();
        roles.deleteAll();
    }

    @Test
    void devuelveNombresFicticiosMarcadosComoSimulados() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogueado", administrador);

        mvc.perform(get("/api/reniec/dni/12345678").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulado").value(true))
                .andExpect(jsonPath("$.nombres").isNotEmpty())
                .andExpect(jsonPath("$.apellidos").isNotEmpty());
    }
}
