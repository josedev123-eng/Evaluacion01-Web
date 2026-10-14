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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// El perfil de pruebas no define RENIEC_TOKEN, así que la consulta externa queda desactivada.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"reniec.api.token=", "reniec.simulado=false"})
class ReniecControllerIntegrationTests {

    @Autowired private MockMvc mvc;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private RolRepository roles;
    @Autowired private PasswordEncoder encoder;

    private Usuario administrador;
    private Usuario sinPermisos;

    @BeforeEach
    void preparar() {
        administrador = usuario("adminReniec", rol("Administrador"));
        sinPermisos = usuario("sinPermisosReniec", rol("Rol sin permisos"));
    }

    @AfterEach
    void limpiar() {
        usuarios.deleteAll();
        roles.deleteAll();
    }

    @Test
    void sinSesionResponde401() throws Exception {
        mvc.perform(get("/api/reniec/dni/12345678"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void sinPermisoParaGestionarPersonasResponde403() throws Exception {
        mvc.perform(get("/api/reniec/dni/12345678").session(sesion(sinPermisos)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unDniQueNoTieneOchoDigitosResponde400() throws Exception {
        mvc.perform(get("/api/reniec/dni/1234").session(sesion(administrador)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/reniec/dni/abcdefgh").session(sesion(administrador)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sinTokenConfiguradoResponde503SinLlamarAlProveedor() throws Exception {
        mvc.perform(get("/api/reniec/dni/12345678").session(sesion(administrador)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    private MockHttpSession sesion(Usuario usuario) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogueado", usuario);
        return session;
    }

    private Rol rol(String nombre) {
        Rol rol = new Rol();
        rol.setNombre(nombre);
        rol.setArea("Administración");
        return roles.saveAndFlush(rol);
    }

    private Usuario usuario(String nombre, Rol principal) {
        Usuario usuario = new Usuario();
        usuario.setNombres("Prueba");
        usuario.setApellidos("Reniec");
        usuario.setUsuario(nombre);
        usuario.setCorreo(nombre + "@pruebas.local");
        usuario.setContrasena(encoder.encode("claveValida"));
        usuario.setRol(principal);
        usuario.setRoles(new LinkedHashSet<>(Set.of(principal)));
        usuario.setArea("Administración");
        return usuarios.saveAndFlush(usuario);
    }
}
