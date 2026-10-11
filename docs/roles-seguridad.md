# Backend de roles y seguridad

**Responsable:** Karim Sovero

Cubre la relación Rol–Permiso (Pregunta 1), la gestión y el estado de los roles
(Pregunta 3) y la seguridad del backend (Pregunta 5).

## Pregunta 1 — Relación Rol–Permiso

- [`Rol.permisos`](../src/main/java/com/evaluacion01/tecsup/entity/Rol.java) es el lado
  principal: `@ManyToMany` con `@JoinTable(name = "rol_permisos")`, `joinColumns = id_rol`
  e `inverseJoinColumns = id_permiso`.
- [`schema.sql`](../src/main/resources/schema.sql) crea `rol_permisos` con clave primaria
  compuesta `(id_rol, id_permiso)` y las claves foráneas `fk_rolpermiso_rol` → `roles` y
  `fk_rolpermiso_permiso` → `permisos`.
- La asignación se hace desde la aplicación con `POST /roles/{id}/permisos`
  ([`RolService.asignarPermisos()`](../src/main/java/com/evaluacion01/tecsup/service/RolService.java)):
  el conjunto enviado reemplaza al anterior, y enviarlo vacío revoca todos los permisos.
- Revocar solo borra filas de `rol_permisos`; el rol y los permisos siguen en sus catálogos.

Consulta para comprobarlo en SQLyog:

```sql
SELECT r.nombre AS rol, p.modulo, p.nombre AS permiso
FROM rol_permisos rp
JOIN roles r ON r.id_rol = rp.id_rol
JOIN permisos p ON p.id_permiso = rp.id_permiso
ORDER BY r.nombre, p.modulo, p.nombre;
```

## Pregunta 3 — Gestión de roles

### Endpoints

Todos exigen sesión, token CSRF en los `POST` y ser Administrador para modificar.

| Método y ruta | Parámetros | Qué hace |
| --- | --- | --- |
| `GET /roles` | — | Lista los roles (requiere `roles:VER`). Agrega al modelo `roles`, `puedeEditar` y `rolesBaseFaltantes` |
| `GET /roles/nuevo` | — | Formulario de registro |
| `GET /roles/{id}/editar` | — | Formulario de edición |
| `POST /roles/guardar` | `idRol` (solo al editar), `nombre`, `descripcion`, `area` | Registra o edita. No acepta `estado` ni permisos |
| `POST /roles/{id}/estado` | `activo=true` o `activo=false` | Activa o desactiva el rol |
| `GET /roles/{id}/permisos` | — | Formulario de permisos del rol |
| `POST /roles/{id}/permisos` | `permisoIds` (cero o más) | Reemplaza los permisos del rol |

Cada `POST` redirige a `/roles` con un mensaje flash `exito` o `error`.

### Reglas del estado de roles

- El estado se guarda en `roles.estado` (`TRUE` por defecto). En las vistas se puede
  leer como `rol.activo`.
- `POST /roles/{id}/estado` recibe el estado deseado, no lo alterna: repetir la petición
  no revierte el cambio.
- Solo un Administrador puede cambiarlo, tanto en el controlador como en
  `RolService.cambiarEstado()`.
- El rol `Administrador` no se puede desactivar.
- Desactivar no borra nada: el rol sigue en el catálogo y en los usuarios que lo tienen.
- Un rol inactivo **no se puede asignar** a usuarios nuevos ni agregarse a usuarios que
  no lo tenían (ni como principal ni como adicional). Quien ya lo tenía lo conserva y
  puede seguir editando sus demás datos.
- El formulario de usuarios (`rolesAsignables`) ya no ofrece los roles inactivos.
- Al reactivarlo vuelve a ser asignable y recupera sus permisos.
- Eventos de auditoría que genera: `ACTIVAR_ROL` y `DESACTIVAR_ROL` (módulo `ROLES`,
  entidad `Rol`, con resultado `EXITO`, `FALLO` o `DENEGADO`).

### Roles base

`Administrador`, `Médico` y `Recepcionista` deben existir siempre. `schema.sql` los
inserta con `INSERT IGNORE` en cada arranque, y `RolService.rolesBaseFaltantes()`
devuelve los que falten (lista vacía si están los tres).

### Cambios de esquema

- `roles.estado BOOLEAN NOT NULL DEFAULT TRUE`.
- Las bases creadas antes de este cambio se actualizan solas al arrancar: `schema.sql`
  agrega la columna únicamente si falta, y todos los roles existentes quedan activos.

## Pregunta 5 — Seguridad del backend

### Qué puede hacer cada rol

| Rol | Usuarios | Roles y permisos | Auditoría | Entra al iniciar sesión en |
| --- | --- | --- | --- | --- |
| Administrador | Ver, crear, editar, activar/desactivar | Ver, crear, editar, activar/desactivar, asignar permisos | Consultar | `/dashboard` |
| Coordinador administrativo | Ver, crear, editar, activar/desactivar (no cuentas administradoras) | Solo ver | Sin acceso | `/usuarios` |
| Auditor administrativo | Solo ver | Solo ver | Sin acceso | `/usuarios` |
| Médico, Recepcionista y demás roles hospitalarios | Sin acceso | Sin acceso | Sin acceso | `/dashboard` |

Los permisos de la tabla son los de la semilla y el Administrador puede cambiarlos desde
`/roles/{id}/permisos`. Crear, editar, activar/desactivar roles y asignar permisos queda
reservado al Administrador aunque otro rol reciba `roles:EDITAR`.

### Cómo se valida

- **Controladores:** cada ruta comprueba la sesión y el permiso antes de ejecutar. Sin
  sesión redirige a `/login`; sin permiso redirige sin ejecutar la acción.
- **Servicios:** `RolService` y `UsuarioServiceImpl` vuelven a validar con
  `AutorizacionService`, por si se llama al servicio sin pasar por la vista.
- **URL directa y peticiones manipuladas:** los permisos se leen de la base de datos en
  cada petición, no del formulario ni de una copia vieja de la sesión. Los campos que no
  pertenecen al formulario (`estado`, permisos) se ignoran, y un `POST` sin token CSRF
  responde 403.
- **Roles inactivos:** `AutorizacionService` descarta los roles inactivos al calcular
  los permisos. El efecto es inmediato, incluso con la sesión ya abierta. Si el usuario
  tiene otro rol activo, conserva los permisos de ese rol.
- **Último administrador activo:** no se puede desactivar su cuenta, quitarle el rol
  Administrador ni desactivar el rol Administrador.
- **Redirección posterior al login:** `AutorizacionService.rutaInicial()` envía al
  Administrador al panel, a quien tiene `usuarios:VER` a `/usuarios`, a quien solo tiene
  `roles:VER` a `/roles` y al resto al panel.

## Pruebas

| Clase | Qué comprueba |
| --- | --- |
| [`RolPermisoRelacionIntegrationTests`](../src/test/java/com/evaluacion01/tecsup/RolPermisoRelacionIntegrationTests.java) | Anotaciones JPA, tabla `rol_permisos`, claves foráneas, asignación, consulta, modificación y revocación |
| [`RolGestionIntegrationTests`](../src/test/java/com/evaluacion01/tecsup/RolGestionIntegrationTests.java) | Registro, listado, edición, activar/desactivar, roles inactivos no asignables y roles base |
| [`SeguridadRolesIntegrationTests`](../src/test/java/com/evaluacion01/tecsup/SeguridadRolesIntegrationTests.java) | Accesos permitidos y rechazados por rol, URL directa, peticiones manipuladas, roles inactivos, último administrador y redirección del login |
| [`AutorizacionServiceTest`](../src/test/java/com/evaluacion01/tecsup/service/AutorizacionServiceTest.java) | Un rol inactivo no otorga permisos |

### Resultados del 9 de octubre de 2026

- `mvnw test` con JDK 21 y H2 en memoria: **196 pruebas, sin fallos**.
- Con JDK 25 fallan 6 pruebas de `AuditoriaServiceTest` por incompatibilidad de Mockito
  con esa versión de Java; ya ocurría antes de estos cambios.
- Ejecución real contra MariaDB 10.4 (XAMPP) sobre una base creada con el esquema
  anterior: la columna `roles.estado` se agregó sola, se insertó el rol `Médico` y se
  comprobaron desde la aplicación el cambio de estado, el rechazo al desactivar
  Administrador, el rechazo al asignar un rol inactivo, la asignación, modificación y
  revocación en `rol_permisos`, la redirección del login y la pérdida inmediata de
  acceso al desactivar el rol de una sesión abierta.
