# Sistema Hospitalario — Evaluación 02

Aplicación web con Spring Boot, Hibernate/JPA, Thymeleaf y MySQL/MariaDB. Parte del
proyecto de la Evaluación 01 e incorpora lo que pide el enunciado de la
**Evaluación N° 02 (Sistema de Gestión de Pedidos / aplicaciones web avanzado)**:
relaciones JPA, auditoría, gestión de usuarios y roles, frontend y control de acceso.

## Qué pide el enunciado y quién lo expone

**Orden de exposición:** 1) José → 2) Karim → 3) Edu → 4) Leonel (la parte más sencilla).

| Pregunta | Qué pide | Quién la expone |
| --- | --- | --- |
| **P1** | Relaciones entre entidades con Hibernate/JPA (mínimo dos por alumno) | Cada integrante, con su propia pareja de entidades |
| **P2** | Auditoría de las operaciones (usuario, fecha y hora, operación, entidad, ID) | Edu |
| **P3** | Gestión de usuarios y roles (backend) | José (usuarios) · Karim (roles) |
| **P4** | Frontend de usuarios y roles | Se muestra como evidencia al demostrar Usuarios (José) y Roles (Karim); no se expone como tema aparte |
| **P5** | Control de acceso según el rol y redirección tras el login | Leonel (demostración por rol) · Karim (reglas y backend) · José (parte compartida) |

| Integrante | Responsabilidad |
| --- | --- |
| **José Saith Rojas Condor** | P1: Usuario–Rol principal. P3: backend de usuarios. P5: control de acceso por rol, parte compartida (incluye el módulo de citas). |
| **Karim Alexander Sovero Campoverde** | P1: Rol–Permiso. P3: backend de roles. P5: autorización y redirección. |
| **Edu Edward Galindo Huaman** | P1: Usuario–roles adicionales. P2: auditoría. Complemento multirrol de P3. |
| **Leonel Mathias Retamozo De la Cruz** | P1: Permiso–Rol (lado inverso). P5: demostración del control de acceso por rol. |

El enunciado indica que cada entidad relacionada se evidencia con **código de las
entidades, base de datos y funcionamiento del CRUD**. Cada sección de integrante
indica dónde está cada una de esas tres cosas.

---

## José Saith Rojas Condor — Usuarios y rol principal

### P1 · Relación Usuario–Rol (`@ManyToOne`)

- **Entidades:** [`Usuario`](src/main/java/com/evaluacion01/tecsup/entity/Usuario.java) → [`Rol`](src/main/java/com/evaluacion01/tecsup/entity/Rol.java).
- **Anotación:** `@ManyToOne` con `@JoinColumn(name = "id_rol")` en `Usuario.rol`.
- **Base de datos:** clave foránea `usuarios.id_rol → roles.id_rol` ([`schema.sql`](src/main/resources/schema.sql)).
- **Integridad:** un usuario siempre tiene un rol principal; un rol referenciado no se elimina.
- **CRUD:** al registrar o editar una cuenta se persiste su rol principal.

### P3 · Backend de usuarios

| Función que pide el enunciado | Dónde está |
| --- | --- |
| Registrar usuarios | `POST /usuarios/guardar` · [`UsuarioController`](src/main/java/com/evaluacion01/tecsup/controller/UsuarioController.java) → [`UsuarioServiceImpl`](src/main/java/com/evaluacion01/tecsup/service/UsuarioServiceImpl.java) |
| Listar usuarios | `GET /usuarios`, con búsqueda por nombre, DNI, correo o usuario |
| Editar usuarios | `GET /usuarios/editar/{id}` y `POST /usuarios/guardar` |
| Activar / desactivar | `POST /usuarios/{id}/estado` (estado explícito, no alterna) |
| Asignar un rol | Rol principal en el formulario de usuario |

Validaciones del servidor: datos obligatorios, usuario/correo/DNI sin duplicados,
referencias existentes, contraseña con BCrypt y la cuenta de un último administrador
activo no puede desactivarse.

### Demostración

1. Entrar como Administrador → **Usuarios**, registrar una cuenta ficticia.
2. Buscarla por DNI o usuario, editarla, desactivarla y reactivarla.
3. Comprobar en SQL: `SELECT id_usuario, usuario, id_rol, estado FROM usuarios;`
4. Intentar un DNI duplicado y explicar por qué el servidor lo rechaza.

### P5 · Control de acceso por rol (parte compartida)

José es el responsable de la sección compartida de la P5: la matriz de acceso por
rol, los pacientes y atenciones del Médico y el **módulo de citas** del
Recepcionista. Se describe en
[P5 · Control de acceso por rol](#p5--control-de-acceso-por-rol-parte-compartida).

**Pruebas:** [`UsuarioGestionIntegrationTests`](src/test/java/com/evaluacion01/tecsup/UsuarioGestionIntegrationTests.java)
y [`CitasIntegrationTests`](src/test/java/com/evaluacion01/tecsup/CitasIntegrationTests.java).

---

## Karim Alexander Sovero Campoverde — Roles, permisos y seguridad

### P1 · Relación Rol–Permiso (`@ManyToMany`)

- **Entidades:** [`Rol`](src/main/java/com/evaluacion01/tecsup/entity/Rol.java) → [`Permiso`](src/main/java/com/evaluacion01/tecsup/entity/Permiso.java).
- **Anotación:** `@ManyToMany` con `@JoinTable(name = "rol_permisos")` en `Rol.permisos`
  (`joinColumns = id_rol`, `inverseJoinColumns = id_permiso`).
- **Base de datos:** tabla `rol_permisos` con clave primaria compuesta y dos claves
  foráneas (`fk_rolpermiso_rol`, `fk_rolpermiso_permiso`).
- **CRUD:** `POST /roles/{id}/permisos` reemplaza el conjunto; enviarlo vacío revoca
  todo. Revocar borra solo filas de `rol_permisos`, no el rol ni el permiso.

```sql
SELECT r.nombre AS rol, p.modulo, p.nombre AS permiso
FROM rol_permisos rp
JOIN roles r ON r.id_rol = rp.id_rol
JOIN permisos p ON p.id_permiso = rp.id_permiso
ORDER BY r.nombre, p.modulo, p.nombre;
```

### P3 · Backend de roles

| Función que pide el enunciado | Dónde está |
| --- | --- |
| Registrar roles | `POST /roles/guardar` · [`RolController`](src/main/java/com/evaluacion01/tecsup/controller/RolController.java) → [`RolService`](src/main/java/com/evaluacion01/tecsup/service/RolService.java) |
| Listar roles | `GET /roles` |
| Editar roles | `GET /roles/{id}/editar` y `POST /roles/guardar` |
| Activar / desactivar | `POST /roles/{id}/estado` (`activo=true` o `false`) |
| Roles mínimos | **Administrador, Médico y Recepcionista**, creados por `schema.sql` |
| Asignar permisos | `GET` y `POST /roles/{id}/permisos` |

Reglas: el nombre del rol no se repite; el rol Administrador no se desactiva ni se
renombra; un rol inactivo no otorga permisos ni se asigna a usuarios nuevos; solo un
Administrador gestiona roles y permisos.

### P5 · Autorización y redirección tras el login

- [`AutorizacionService`](src/main/java/com/evaluacion01/tecsup/service/AutorizacionService.java)
  combina los permisos de todos los roles activos del usuario, leyéndolos de la base
  en cada petición (no de una copia vieja de la sesión). Una cuenta o un rol inactivo
  pierde el acceso de inmediato, incluso con la sesión abierta.
- `rutaInicial()` decide a dónde entra cada rol después del login (ver la matriz de P5).
- Los controladores comprueban sesión y permiso antes de ejecutar, y los servicios
  vuelven a validar, por si se llama a un servicio sin pasar por la vista.
- Se impide la escalada de privilegios: nadie asigna permisos o roles que él mismo no
  posee, ni cambia sus propios roles.

### Demostración

1. Registrar un rol, asignarle permisos, desactivarlo y reactivarlo; comprobar
   `rol_permisos` y `roles.estado` en SQL.
2. Entrar con una cuenta autorizada y con otra restringida; probar también una URL
   directa a una pantalla no permitida.
3. Explicar multirrol, rol inactivo, último administrador y redirección del login.

**Pruebas:** [`RolPermisoRelacionIntegrationTests`](src/test/java/com/evaluacion01/tecsup/RolPermisoRelacionIntegrationTests.java),
[`RolGestionIntegrationTests`](src/test/java/com/evaluacion01/tecsup/RolGestionIntegrationTests.java),
[`SeguridadRolesIntegrationTests`](src/test/java/com/evaluacion01/tecsup/SeguridadRolesIntegrationTests.java),
[`AutorizacionServiceTest`](src/test/java/com/evaluacion01/tecsup/service/AutorizacionServiceTest.java).
Guía ampliada: [docs/roles-seguridad.md](docs/roles-seguridad.md).

---

## Edu Edward Galindo Huaman — Auditoría y multirrol

### P1 · Relación Usuario–roles adicionales (`@ManyToMany`)

- **Entidades:** [`Usuario`](src/main/java/com/evaluacion01/tecsup/entity/Usuario.java) → [`Rol`](src/main/java/com/evaluacion01/tecsup/entity/Rol.java).
- **Anotación:** `@ManyToMany` con `@JoinTable(name = "usuario_roles")` en `Usuario.roles`.
- **Base de datos:** tabla `usuario_roles` con sus dos claves foráneas.
- **CRUD:** añadir o quitar un rol adicional conserva el rol principal. No hay
  cascadas que modifiquen o borren roles compartidos.

### P2 · Auditoría

Registra las operaciones críticas del sistema en la tabla `auditoria_logs`
([`AuditoriaLog`](src/main/java/com/evaluacion01/tecsup/entity/AuditoriaLog.java),
[`AuditoriaService`](src/main/java/com/evaluacion01/tecsup/service/AuditoriaService.java)).

| Dato que exige el enunciado | Columna |
| --- | --- |
| Usuario que realizó la operación | `usuario_ejecutor` (y `id_usuario_ejecutor`) |
| Fecha y hora | `fecha_hora` (se guarda en UTC y se muestra en hora de Lima) |
| Operación realizada | `accion` (y `modulo`) |
| Entidad afectada | `entidad` |
| Identificador del registro | `id_entidad` |

Además guarda `resultado` (ÉXITO, FALLO o DENEGADO), `detalle`, origen (`ip`,
`metodo_http`, `ruta`). Nunca guarda contraseñas, tokens ni textos clínicos.

- **Operaciones demostrables:** registro y modificación de usuarios, roles,
  permisos, pacientes, atenciones y citas; activación y desactivación; inicio de
  sesión correcto y fallido.
- **Eliminación:** una **cita no atendida se puede eliminar** desde `/citas`; la
  operación queda en la auditoría como `ELIMINAR_CITA` con su ID. El resto de
  entidades usa activación/desactivación para conservar el historial.
- **Consulta:** `GET /auditoria`, solo para Administrador, con filtros por usuario,
  módulo, resultado y rango de fechas ([`AuditoriaController`](src/main/java/com/evaluacion01/tecsup/controller/AuditoriaController.java)).

### Demostración

1. Registrar o editar una cuenta y cambiar su estado.
2. Como Administrador, abrir **Auditoría** y mostrar ejecutor, fecha y hora, acción,
   entidad, ID y resultado.
3. Mostrar una operación rechazada y los filtros.
4. Comprobar en SQL: `SELECT * FROM auditoria_logs ORDER BY id_auditoria DESC LIMIT 20;`

**Pruebas:** [`MultirrolIntegracionTests`](src/test/java/com/evaluacion01/tecsup/MultirrolIntegracionTests.java)
y las pruebas `Auditoria*IntegrationTests`. Guía: [docs/auditoria.md](docs/auditoria.md).

---

## Leonel Mathias Retamozo De la Cruz — Control de acceso por rol (P5)

Expone **lo que pide el documento**, sin entrar al lado de frontend. Su parte es la
más fácil: **inicia sesión con cada rol y muestra que cada uno solo ve lo suyo.**

### P1 · Relación Permiso–Rol (código y base de datos)

- **Entidades:** [`Permiso`](src/main/java/com/evaluacion01/tecsup/entity/Permiso.java) y [`Rol`](src/main/java/com/evaluacion01/tecsup/entity/Rol.java).
- **Qué decir:** es la **misma tabla `rol_permisos`** que usa Karim, pero mirada al
  revés: desde un permiso se puede saber **qué roles lo usan**. Se logra con
  `@ManyToMany(mappedBy = "permisos")` en `Permiso.roles` y **no crea otra tabla**.

### P5 · Control de acceso según el rol (lo que pide el enunciado)

El documento pide que cada rol **vea solo lo permitido**, que el acceso se **valide
en el backend** y que el login **redirija según el rol** (matriz completa en
[P5 · Control de acceso por rol](#p5--control-de-acceso-por-rol-parte-compartida)).

- **Solo las opciones permitidas:** el menú cambia según el rol.
- **Impide el acceso no autorizado:** abrir por la URL una pantalla no permitida
  responde 403, y el backend vuelve a validar cada acción.
- **Redirección tras el login:** cada rol entra a su pantalla
  (`AutorizacionService.rutaInicial()`).

### Demostración (fácil, lo del documento)

1. Iniciar sesión como **Médico**: mostrar que solo ve Pacientes y Atenciones.
2. Iniciar sesión como **Recepcionista**: mostrar que solo ve Pacientes y Citas.
3. Intentar abrir por la URL una pantalla no permitida y mostrar que el sistema la
   bloquea (403).

**Pruebas:** [`SeguridadRolesIntegrationTests`](src/test/java/com/evaluacion01/tecsup/SeguridadRolesIntegrationTests.java),
[`PermisoRolInversoIntegrationTests`](src/test/java/com/evaluacion01/tecsup/PermisoRolInversoIntegrationTests.java),
[`CitasIntegrationTests`](src/test/java/com/evaluacion01/tecsup/CitasIntegrationTests.java) (acceso de Médico y Recepción).
Guía individual: [docs/retamozo-evaluacion02.md](docs/retamozo-evaluacion02.md).

---

## P5 · Control de acceso por rol (parte compartida)

**Responsable:** José Saith Rojas Condor.

Las reglas de autorización y la redirección tras el login son de Karim, y los menús
y las vistas de Leonel (ver sus secciones); esta parte compartida reúne el resultado
completo por rol, los pacientes y atenciones del Médico y el módulo de citas del
Recepcionista.

El enunciado da este ejemplo de acceso y el sistema lo cumple:

| Rol | Acceso que pide el enunciado | Qué puede hacer en el sistema | Entra al iniciar sesión en |
| --- | --- | --- | --- |
| **Administrador** | Usuarios, roles y módulos | Usuarios, roles y permisos, auditoría, pacientes, atenciones y citas | `/dashboard` |
| **Médico** | Pacientes e historias clínicas | Ver pacientes, registrar atenciones y consultar las propias, ver sus citas | `/pacientes` |
| **Recepcionista** | Pacientes y citas | Ver, registrar y editar pacientes; programar y gestionar citas | `/pacientes` |

El sistema, además:

- **Muestra únicamente las opciones permitidas** en el menú y el tablero.
- **Impide el acceso no autorizado** y lo valida en el **backend**: una URL directa a
  una pantalla no permitida redirige o responde 403 sin ejecutar la acción.
- **Redirecciona después del inicio de sesión** según los permisos (ver
  `AutorizacionService.rutaInicial()`).

### Pacientes y atenciones (historias clínicas del Médico)

Un paciente no es una cuenta de acceso. El Médico ve solo sus propias atenciones; el
Administrador puede revisar todas. Las atenciones no se editan ni se eliminan.
Guía: [docs/modulo-clinico.md](docs/modulo-clinico.md).

### Citas (Recepcionista: pacientes y citas)

| Función | Detalle |
| --- | --- |
| Programar cita | Paciente, médico, fecha y hora (hora de Lima) y motivo. Solo hacia el futuro; un médico no tiene dos citas programadas a la misma hora; el médico debe tener una cuenta activa con permiso de atender |
| Listar agenda | Administrador y Recepción ven todas; cada Médico ve únicamente las suyas (lo decide el servidor, no la URL) |
| Atender / cancelar / eliminar | Una cita pasa de PROGRAMADA a ATENDIDA o CANCELADA; una cita no atendida se puede eliminar |
| Relación JPA | [`Cita`](src/main/java/com/evaluacion01/tecsup/entity/Cita.java): `@ManyToOne` a `Paciente` y a `Usuario` (médico y quien la programó) |
| Auditoría | `REGISTRAR_CITA`, `ATENDER_CITA`, `CANCELAR_CITA`, `ELIMINAR_CITA` (módulo `CITAS`); registrar y eliminar usan AOP (`@Auditable`), sin nombres ni DNI |

Código: [`CitaService`](src/main/java/com/evaluacion01/tecsup/service/CitaService.java),
[`CitaController`](src/main/java/com/evaluacion01/tecsup/controller/CitaController.java),
[`citas/lista.html`](src/main/resources/templates/citas/lista.html).
Prueba: [`CitasIntegrationTests`](src/test/java/com/evaluacion01/tecsup/CitasIntegrationTests.java).

**Permisos:** `schema.sql` concede al Recepcionista `pacientes` y `citas`
(ver, crear, editar) **una sola vez**, mientras no exista ningún permiso del módulo
`citas`; después no reasigna nada, así que lo que un Administrador revoque se
respeta. Los permisos clínicos de los médicos (`pacientes:VER`, `atenciones:VER` y
`atenciones:CREAR`) se importan **a mano, una sola vez**, con
[`permisos-clinicos.sql.example`](src/main/resources/permisos-clinicos.sql.example).

---

## Cómo ejecutar

1. Tener MySQL o MariaDB en `localhost:3306` (XAMPP sirve). La base `bd_hospital` se
   crea sola.
2. Usuario y contraseña de la base: variables de entorno `DB_USERNAME` (por defecto
   `root`) y `DB_PASSWORD` (por defecto vacía). No se escriben en el repositorio.
3. Ejecutar la aplicación:

   ```
   ./mvnw spring-boot:run
   ```

   o ejecutar `TecsupApplication` desde IntelliJ. Al arrancar, `schema.sql` crea las
   tablas que falten y los roles base (Administrador, Médico, Recepcionista).
4. Abrir `http://127.0.0.1:8080/login`.
5. Para una base nueva y vacía, importar a mano, una sola vez y en este orden,
   [`data.sql.example`](src/main/resources/data.sql.example) y
   [`permisos-clinicos.sql.example`](src/main/resources/permisos-clinicos.sql.example).
   Ninguno se ejecuta al arrancar.

**Demostración aislada** (no usa MySQL; datos en memoria y cuentas ficticias que se
muestran en la consola):

```
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo-retamozo.ps1
```

Abre `http://127.0.0.1:8095/login`.

## Pruebas

```
./mvnw test
```

- **304 pruebas**, con H2 en memoria (no tocan tu MySQL).
- Con **JDK 21** pasan todas. Con **JDK 25** fallan las 6 de `AuditoriaServiceTest`
  por incompatibilidad de Mockito con esa versión de Java; las otras 298 pasan.
- Ejecución contra servidor SQL real comprobada en **MariaDB 10.4 (XAMPP)**; MySQL 8
  no se ha probado.

## Para la exposición

Orden recomendado para cada integrante: **código de la entidad → tabla y clave
foránea en SQL → operación en la aplicación → una prueba positiva y el rechazo de
un caso inválido**. Todos los datos de demostración son ficticios.
