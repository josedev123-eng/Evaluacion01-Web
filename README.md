# Evaluacion01-Web — Sistema Hospitalario (Tecsup) | Prueba 1

Proyecto Spring Boot que integra los 8 requerimientos de la Evaluación 01, cada uno
desarrollado originalmente en una rama/zip independiente por un integrante distinto y
fusionado aquí en una sola aplicación.

## Alcance de esta entrega

Esta es la **Prueba 1**, una entrega académica de demostración local. Incluye acceso,
recuperación de contraseña y administración de usuarios, áreas, roles y permisos.
No está preparada para producción ni debe exponerse a Internet con la configuración
y credenciales de ejemplo actuales. Los problemas conocidos quedan registrados al
final de este documento para las siguientes entregas.

Los roles hospitalarios sirven como catálogo de personal. Todavía no existen módulos
de pacientes, citas, atención médica, laboratorio o medicamentos. Un médico no recibe
acceso administrativo a usuarios por pertenecer al área de Medicina. Para los roles
sin acceso a módulos actuales, **más adelante se agregarán nuevas funciones**; por
ahora el dashboard informa que el rol no tiene acceso a otras secciones.

## Tecnologías

- Java 17 como versión objetivo y Spring Boot 3.2.5.
- Maven Wrapper, Spring MVC, Thymeleaf, JPA/Hibernate y MySQL.
- BCrypt para contraseñas, sesiones HTTP y autorización manual por permisos.
- Bootstrap 5 y Bootstrap Icons mediante CDN.

## Requerimientos

### RF-LOGIN-01 — Inicio de sesión con usuario/correo y contraseña
**Responsable:** Galindo Huaman Edu Edward
**Para qué sirve:** permite que cualquier usuario registrado entre al sistema
identificándose con su nombre de usuario *o* su correo, más su contraseña.
**Dónde está:**
- [`LoginController`](src/main/java/com/evaluacion01/tecsup/controller/LoginController.java) — `GET /login` muestra el formulario, `POST /login` procesa el envío.
- [`login.html`](src/main/resources/templates/login.html) — formulario de acceso.
- [`AuthService.autenticar()`](src/main/java/com/evaluacion01/tecsup/service/AuthService.java) — busca al usuario por usuario o correo.
- [`UsuarioRepository.findByUsuarioOrCorreo()`](src/main/java/com/evaluacion01/tecsup/repository/UsuarioRepository.java) — consulta a la base de datos.

### RF-LOGIN-02 — Validación de credenciales antes del acceso
**Responsable:** Galindo Huaman Edu Edward
**Para qué sirve:** evita que alguien entre sin una contraseña correcta o con una
cuenta desactivada; solo si todo es válido se crea la sesión.
**Dónde está:**
- [`AuthService.autenticar()`](src/main/java/com/evaluacion01/tecsup/service/AuthService.java) — compara la contraseña con `PasswordEncoder.matches()` (BCrypt) y revisa el campo `estado` del usuario.
- [`SecurityConfig.passwordEncoder()`](src/main/java/com/evaluacion01/tecsup/config/SecurityConfig.java) — define el `BCryptPasswordEncoder` usado para el hash/verificación.
- [`LoginController.processLogin()`](src/main/java/com/evaluacion01/tecsup/controller/LoginController.java) — solo guarda al usuario en sesión (`HttpSession`) si `autenticar()` no lanzó una excepción.

### RF-LOGIN-03 — Mensaje cuando las credenciales son incorrectas
**Responsable:** Retamozo De la Cruz Leonel Mathias
**Para qué sirve:** le informa al usuario por qué no pudo entrar (usuario
inexistente, contraseña incorrecta, cuenta inactiva) en vez de fallar en silencio.
**Dónde está:**
- [`AuthService.autenticar()`](src/main/java/com/evaluacion01/tecsup/service/AuthService.java) — lanza excepciones con el mensaje específico de cada caso.
- [`LoginController.processLogin()`](src/main/java/com/evaluacion01/tecsup/controller/LoginController.java) — captura la excepción y la pasa a la vista como `error`.
- [`login.html`](src/main/resources/templates/login.html) — muestra el mensaje en una alerta Bootstrap cuando existe `error`.

### RF-LOGIN-04 — Recuperación de contraseña
**Responsable:** Retamozo De la Cruz Leonel Mathias
**Para qué sirve:** deja que un usuario que olvidó su contraseña pida un token por
correo y lo use para definir una nueva, sin depender de un administrador.
En la configuración local predeterminada, el envío es simulado: el token aparece en
la consola del servidor. El envío real requiere configurar SMTP.
**Dónde está:**
- [`ViewController`](src/main/java/com/evaluacion01/tecsup/controller/ViewController.java) — sirve las páginas `/forgot-password` y `/reset-password`.
- [`forgot-password.html`](src/main/resources/templates/forgot-password.html) / [`reset-password.html`](src/main/resources/templates/reset-password.html) — formularios que llaman a la API por JavaScript (`fetch`).
- [`PasswordRecoveryController`](src/main/java/com/evaluacion01/tecsup/controller/PasswordRecoveryController.java) — expone `POST /api/v1/auth/forgot-password` y `POST /api/v1/auth/reset-password`.
- [`PasswordRecoveryService`](src/main/java/com/evaluacion01/tecsup/service/PasswordRecoveryService.java) — genera el token, lo guarda con expiración de 15 minutos, y actualiza la contraseña (hasheada) cuando el token es válido.
- [`ForgotPasswordDto` / `ResetPasswordDto` / `ApiResponseDto`](src/main/java/com/evaluacion01/tecsup/dto/) — datos de entrada/salida de la API.
- [`GlobalExceptionHandler`](src/main/java/com/evaluacion01/tecsup/exception/GlobalExceptionHandler.java) — convierte los errores de este flujo en respuestas JSON entendibles.

### RF-USR-01 — El administrador registra usuarios
**Responsable:** Jose Rojas Condor
**Para qué sirve:** permite dar de alta nuevos usuarios del sistema (nombre, DNI,
correo, usuario, contraseña, área y rol) desde una pantalla, sin tocar la base de datos a mano.
**Dónde está:**
- [`UsuarioController.listar()` / `guardar()`](src/main/java/com/evaluacion01/tecsup/controller/UsuarioController.java) — `GET /usuarios` muestra el formulario y la lista, `POST /usuarios/guardar` crea el usuario cuando no trae `idUsuario`.
- [`formulario.html`](src/main/resources/templates/formulario.html) — formulario de alta/edición y tabla de usuarios registrados.
- [`UsuarioServiceImpl.registrarUsuario()`](src/main/java/com/evaluacion01/tecsup/service/UsuarioServiceImpl.java) — hashea la contraseña y guarda el usuario.
- [`Usuario`](src/main/java/com/evaluacion01/tecsup/entity/Usuario.java) / [`UsuarioRepository`](src/main/java/com/evaluacion01/tecsup/repository/UsuarioRepository.java) — entidad y acceso a datos.

### RF-USR-02 — El administrador modifica los datos de los usuarios
**Responsable:** Jose Rojas Condor
**Para qué sirve:** permite corregir o actualizar los datos de un usuario ya
existente (incluyendo cambiar su contraseña o su rol) sin crear uno nuevo.
**Dónde está:**
- [`UsuarioController.mostrarFormularioEditar()` / `guardar()`](src/main/java/com/evaluacion01/tecsup/controller/UsuarioController.java) — `GET /usuarios/editar/{id}` precarga el formulario, `POST /usuarios/guardar` actualiza cuando sí trae `idUsuario`.
- [`UsuarioServiceImpl.actualizarUsuario()`](src/main/java/com/evaluacion01/tecsup/service/UsuarioServiceImpl.java) — actualiza los datos enviados y conserva la contraseña si se deja vacía en edición.
- **Activar/desactivar usuario** — botón por cada fila de la tabla en `formulario.html` que llama a `POST /usuarios/{id}/estado` ([`UsuarioController.cambiarEstado()`](src/main/java/com/evaluacion01/tecsup/controller/UsuarioController.java)), el cual invierte el campo `estado` ([`UsuarioServiceImpl.cambiarEstado()`](src/main/java/com/evaluacion01/tecsup/service/UsuarioServiceImpl.java)). Sirve, por ejemplo, para cuando alguien deja de trabajar en el hospital: se le desactiva en vez de borrarlo, y `AuthService` ya bloqueaba el login de cuentas inactivas — pero antes no existía ninguna forma de marcarlas así desde la interfaz.

### RF-USR-03 — Corrección de la activación/desactivación de usuarios
**Responsable:** Jose Rojas Condor
**Para qué sirve:** que desactivar a un usuario funcione de verdad y no se pueda deshacer por accidente.
**Qué se corrigió:**
- Editar a un usuario inactivo lo volvía a activar, porque el formulario no envía `estado` y la entidad lo inicia en `true`. Ahora [`UsuarioServiceImpl.actualizarUsuario()`](src/main/java/com/evaluacion01/tecsup/service/UsuarioServiceImpl.java) no toca el estado.
- El botón alternaba el estado (un doble clic lo revertía). Ahora `POST /usuarios/{id}/estado` recibe `activo=true|false` y pide confirmación en [`formulario.html`](src/main/resources/templates/formulario.html).
- Un usuario desactivado seguía navegando con su sesión abierta. [`SesionActivaInterceptor`](src/main/java/com/evaluacion01/tecsup/config/SesionActivaInterceptor.java) recarga al usuario en cada petición y, si ya no está activo, cierra su sesión y lo manda a `/login?cuentaInactiva`.
- No se puede desactivar la propia cuenta ni al último administrador activo ([`UsuarioServiceImpl.cambiarEstado()`](src/main/java/com/evaluacion01/tecsup/service/UsuarioServiceImpl.java)).

### RF-USR-04 — Asignar múltiples roles a un usuario
**Responsable:** Jose Rojas Condor
**Para qué sirve:** un mismo usuario puede tener varios roles de su área (por ejemplo, Médico general y Médico especialista).
**Dónde está:**
- [`Usuario.roles`](src/main/java/com/evaluacion01/tecsup/entity/Usuario.java) (tabla `usuario_roles` en [`schema.sql`](src/main/resources/schema.sql)). `usuarios.id_rol` se mantiene como **rol principal** y siempre está incluido en `roles`. `getRolesAsignados()` devuelve el principal primero y luego los demás.
- El formulario tiene casillas "Roles adicionales" filtradas por el área elegida; la tabla muestra todos los roles (el principal resaltado).
- Los usuarios existentes se migran solos: `schema.sql` copia su `id_rol` a `usuario_roles`.
- `AutorizacionService` combina los permisos de todos los roles asignados (RF-ROL-03).

### RF-USR-05 — Búsqueda y filtros de usuarios
**Responsable:** Jose Rojas Condor
**Para qué sirve:** encontrar rápido a un usuario cuando la lista crece.
**Dónde está:**
- `GET /usuarios?q=&area=&idRol=&estado=activos|inactivos` en [`UsuarioController.listar()`](src/main/java/com/evaluacion01/tecsup/controller/UsuarioController.java).
- [`UsuarioRepository.buscar()`](src/main/java/com/evaluacion01/tecsup/repository/UsuarioRepository.java): el texto busca en nombres, apellidos, nombre completo, DNI, correo y usuario; el rol coincide si es el principal o uno adicional.
- Barra de filtros encima de la tabla en [`formulario.html`](src/main/resources/templates/formulario.html), con botón para limpiarlos.

### RF-ROL-01 — El administrador registra y modifica roles
**Responsable:** Sovero Campoverde Karim Alexander
**Para qué sirve:** permite crear los roles del sistema (por ejemplo "Administrador",
"Médico") y editar su nombre, descripción y área, para luego asignárselos a los usuarios.
**Dónde está:**
- [`RolController.listar()` / `nuevoForm()` / `editarForm()` / `guardar()`](src/main/java/com/evaluacion01/tecsup/controller/RolController.java) — rutas `GET /roles`, `GET /roles/nuevo`, `GET /roles/{id}/editar`, `POST /roles/guardar`.
- [`roles/lista-roles.html`](src/main/resources/templates/roles/lista-roles.html) / [`roles/form-rol.html`](src/main/resources/templates/roles/form-rol.html) — listado y formulario.
- [`RolService.guardar()`](src/main/java/com/evaluacion01/tecsup/service/RolService.java) — valida nombre único y crea/actualiza.
- [`Rol`](src/main/java/com/evaluacion01/tecsup/entity/Rol.java) / [`RolRepository`](src/main/java/com/evaluacion01/tecsup/repository/RolRepository.java) — entidad y acceso a datos.

### RF-ROL-02 — El administrador asigna permisos a cada rol
**Responsable:** Sovero Campoverde Karim Alexander
**Para qué sirve:** define qué puede hacer cada rol, marcando qué permisos
(por módulo, ej. "usuarios: EDITAR") tiene asignados.
**Dónde está:**
- [`RolController.asignarPermisosForm()` / `asignarPermisos()`](src/main/java/com/evaluacion01/tecsup/controller/RolController.java) — `GET`/`POST /roles/{id}/permisos`.
- [`roles/asignar-permisos.html`](src/main/resources/templates/roles/asignar-permisos.html) — checklist de permisos por módulo.
- [`RolService.asignarPermisos()`](src/main/java/com/evaluacion01/tecsup/service/RolService.java) — reemplaza el conjunto de permisos del rol.
- [`Permiso`](src/main/java/com/evaluacion01/tecsup/entity/Permiso.java) / [`PermisoRepository`](src/main/java/com/evaluacion01/tecsup/repository/PermisoRepository.java) — entidad y acceso a datos.
- Tabla intermedia `rol_permisos` (ver [`schema.sql`](src/main/resources/schema.sql)) — relación muchos-a-muchos entre roles y permisos.
- **Se comprueban permisos por módulo y acción**, no solo se guardan:
  [`AutorizacionService.tienePermiso()`](src/main/java/com/evaluacion01/tecsup/service/AutorizacionService.java)
  revisa si alguno de los roles del usuario logueado tiene el permiso (módulo + nombre) requerido
  para cada acción. `UsuarioController` y `RolController` llaman a este servicio antes
  de listar, crear, editar o cambiar el estado — si no tiene el permiso, se le
  redirige sin ejecutar la acción, con un mensaje de "Tu rol no tiene permiso...".
  [`NavegacionModelAdvice`](src/main/java/com/evaluacion01/tecsup/controller/NavegacionModelAdvice.java)
  además oculta los enlaces "Usuarios"/"Roles" del menú y los botones "Editar" en las
  tablas cuando el rol no tiene el permiso correspondiente. El rol llamado
  **"Administrador"** (por nombre, sin importar mayúsculas) tiene acceso total
  automático, sin depender de sus permisos asignados — así no te quedas fuera del
  sistema si a ese rol se le olvida marcar algún checkbox.

### RF-ROL-03 — Permisos para múltiples roles
**Responsable:** Sovero Campoverde Karim Alexander
**Para qué sirve:** las acciones y la navegación utilizan la unión de los permisos
del rol principal y de los adicionales. No es necesario cambiar el rol principal
para aprovechar un permiso de otro rol.
**Dónde está:** [`AutorizacionService`](src/main/java/com/evaluacion01/tecsup/service/AutorizacionService.java).
- Los usuarios anteriores sin filas en `usuario_roles` conservan los permisos del principal.
- Administrador concede acceso total tanto como principal como adicional.
- Se verifica la cuenta persistida y su estado, no nombres de roles enviados en un
  formulario ni una copia obsoleta de la sesión. Cambiar permisos se refleja en la
  siguiente petición; una cuenta inactiva o eliminada no conserva acceso.
- La protección del último administrador cuenta cuentas distintas con Administrador
  en cualquiera de sus roles, y también impide quitarle ese rol al último activo.

### RF-ROL-04 — Impedir accesos indebidos y autoasignación de Administrador
**Responsable:** Sovero Campoverde Karim Alexander
**Reglas aplicadas en el servidor:**
- Solo un Administrador activo puede crear/editar roles o asignar sus permisos.
  `roles:EDITAR` no concede esa facultad a otras cuentas, aunque esté asignado en la BD.
- Solo un Administrador puede asignar Administrador, ya sea principal o adicional,
  crear cuentas administrativas, editar sus datos/contraseña o cambiar su estado.
- Un operador no administrador puede modificar sus datos personales, pero no su
  conjunto de roles. Tampoco puede conceder permisos que no posee ni modificar
  cuentas con permisos superiores para tomar su contraseña.
- El nombre del rol Administrador es reservado: no se puede renombrar para quitar
  o conceder acceso total por esa vía.
- Los servicios de usuarios y roles reciben al operador autenticado y validan la
  operación antes de modificar entidades; no basta con ocultar botones.
- Los formularios solo enlazan campos permitidos. No aceptan inyección de
  `estado`, tokens de recuperación, asociaciones de roles o permisos anidados.
- Usuarios, roles y dashboard exigen sesión. Los formularios POST requieren CSRF;
  Thymeleaf inserta el token automáticamente. Los dos endpoints públicos de
  recuperación de contraseña mantienen su flujo JSON sin token CSRF.

La interfaz oculta Administrador de los roles asignables a operadores, identifica
cuentas protegidas y conserva el catálogo completo en los filtros de búsqueda.
Estas reglas no implementan auditoría ni envío de correo real: corresponden a las
ramas de Edu y Retamozo.

## Historial de integración

Esta sección describe cambios realizados durante la integración, no instrucciones
para instalar la versión actual. El esquema actual es `schema.sql` y los datos
iniciales se importan manualmente desde `data.sql.example`.

Cada zip traía su propia copia de entidades duplicadas (`Usuario`/`User`, `Rol`/`Role`,
`Permiso`/`Permission`, en español e inglés) sin historial de git compartido. Se unificó
todo bajo nombres en español y se corrigieron varios problemas que impedían que las
piezas funcionaran juntas:

- El formulario de usuarios (`formulario.html`) enviaba las ediciones a una ruta
  (`/usuarios/actualizar/{id}`) que el controlador nunca definió: se corrigió para
  que siempre publique a `/usuarios/guardar`.
- El mismo formulario no mostraba el listado de usuarios ni cargaba la lista de roles
  en el `<select>`: se agregó la tabla y se pasa `roles` al modelo.
- `th:field="*{rol.idRol}"` fallaba al crear un usuario nuevo porque `rol` era `null`:
  el controlador ahora inicializa un `Rol` vacío para el formulario de alta.
- Las contraseñas se comparaban y guardaban en texto plano; ahora se usan
  `PasswordEncoder` (BCrypt) tanto al login como al crear/editar usuarios y al
  restablecer la contraseña.
- `SecurityConfig` exigía autenticación de Spring Security en todas las rutas, pero el
  login es manual por sesión HTTP (no usa el contexto de seguridad de Spring): se
  ajustó para permitir todas las rutas y solo aportar el `PasswordEncoder`. Las páginas
  de usuarios y roles ahora validan la sesión manualmente en el controlador.
- `login.html` estaba en `resources/temaplates` (typo) en vez de `resources/templates`.
- Se agregó `dashboard.html`, que no existía en ningún zip pese a que el login
  redirige ahí.
- Se agregó navegación (usuarios ⇄ roles ⇄ dashboard) para que la app se sienta como
  un solo sistema.
- Inicialmente se agregaron `schema.sql` y `data.sql` porque `ddl-auto=none` no crea
  las tablas por sí solo. Después se retiró el seed automático: ahora se usa
  `data.sql.example` únicamente mediante importación manual.

## Mejoras recientes de interfaz y catálogo hospitalario

- Se renovaron todas las vistas Thymeleaf (`login`, recuperación de contraseña,
  dashboard, usuarios, roles, permisos y errores) con una identidad visual hospitalaria
  consistente, diseño adaptable a móvil, iconos y componentes Bootstrap reutilizables.
- Se añadió la hoja de estilos compartida
  [`static/css/app.css`](src/main/resources/static/css/app.css), que unifica navegación,
  tarjetas, formularios, tablas, botones, alertas y estados visuales.
- El dashboard ahora presenta accesos visuales a los módulos disponibles según los permisos
  del usuario autenticado. La navegación marca la sección actual y mantiene ocultas las
  opciones sin autorización.
- El formulario de usuarios organiza los datos en secciones de información personal y
  acceso al sistema. El campo **Área** dejó de ser texto libre y ahora es obligatorio
  mediante un menú con: `Administración`, `Medicina`, `Enfermería`, `Recepción`,
  `Laboratorio` y `Farmacia`.
- Cada rol pertenece a una sola área. Al seleccionar un área en el formulario de usuario,
  el selector de roles muestra únicamente los perfiles de esa área; el controlador vuelve
  a comprobar la relación antes de guardar para impedir asignaciones inconsistentes.
- Los permisos de un rol se presentan como tarjetas seleccionables para facilitar su
  configuración visual. La lógica de asignación no cambia: al guardar se reemplaza el
  conjunto de permisos del rol.
- Se amplió [`data.sql.example`](src/main/resources/data.sql.example) con el catálogo
  inicial de roles por área. El rol `Administrador` conserva acceso total automático.

| Área | Roles iniciales |
| --- | --- |
| Administración | Administrador, Coordinador administrativo, Auditor administrativo |
| Medicina | Médico general, Médico especialista |
| Enfermería | Enfermero, Técnico de enfermería |
| Recepción | Recepcionista, Auxiliar de recepción |
| Laboratorio | Analista de laboratorio, Técnico de laboratorio |
| Farmacia | Farmacéutico, Auxiliar de farmacia |

La semilla contiene **13 roles, 5 permisos y 1 cuenta inicial**. La cuenta de
demostración es `admin` con contraseña `admin123`. Importar la semilla no borra ni
reinicia una base existente; el reinicio realizado durante el desarrollo fue una
operación manual exclusiva de la base local.

| Rol | Permisos iniciales |
| --- | --- |
| Administrador | Todos los permisos de usuarios y roles |
| Coordinador administrativo | Ver, crear y editar usuarios; ver roles |
| Auditor administrativo | Solo ver usuarios y roles |
| Roles clínicos, recepción, laboratorio y farmacia | Sin permisos administrativos hasta incorporar sus módulos funcionales |

### Permisos disponibles

| Permiso | Operaciones habilitadas |
| --- | --- |
| `usuarios:VER` | Consultar el listado de cuentas |
| `usuarios:CREAR` | Registrar cuentas |
| `usuarios:EDITAR` | Editar cuentas y activar/desactivar su estado |
| `roles:VER` | Consultar el listado de roles |
| `roles:EDITAR` | Crear/editar roles y asignar sus permisos, reservado al Administrador |

El área filtra los roles disponibles, pero **no concede permisos** por sí misma.
No existe un permiso independiente `roles:CREAR` en esta entrega. El rol denominado
`Administrador` tiene acceso total por su nombre, sin distinguir mayúsculas.
La semilla usa `INSERT IGNORE`: añade datos faltantes, pero no elimina permisos
anteriores ni restaura exactamente una configuración modificada. Debe importarse
una sola vez en una instalación nueva.

## Errores encontrados y corregidos después de la primera puesta en marcha

Al probar la app ya corriendo (con MySQL real) aparecieron varios problemas que no se
veían solo compilando. Quedan documentados aquí porque son justo el tipo de cosas que
pueden volver a pasar si se sigue tocando el código:

- **Tablas ya existentes con columnas de menos.** Si `bd_hospital` ya tenía una tabla
  `usuarios` de pruebas previas, `CREATE TABLE IF NOT EXISTS` no la actualiza — hubo
  que agregarle a mano las columnas `reset_token` y `reset_token_expiry` con
  `ALTER TABLE`. Si te pasa lo mismo, compara tu tabla contra `schema.sql`.
- **Contraseñas viejas en texto plano.** Cuentas de prueba creadas antes de la fusión
  (cuando el login aún comparaba contraseñas sin hash) no podían iniciar sesión
  después, porque `AuthService` ahora usa `PasswordEncoder.matches()`. Se re-hashean
  una sola vez con BCrypt manteniendo la misma clave.
- **`spring.mail.host` configurado por defecto rompía "Olvidé mi contraseña".**
  Con el host puesto (aunque el usuario/contraseña de correo estuvieran vacíos),
  Spring Boot igual crea un `JavaMailSender` real y trata de autenticarse contra
  Gmail sin credenciales, mostrando "Authentication failed". Ahora ese bloque está
  comentado por defecto: sin `spring.mail.host`, no se crea el bean y
  `PasswordRecoveryService` cae al modo "imprime el token en consola".
- **`GlobalExceptionHandler` interceptaba TODOS los controladores, no solo el de
  recuperación de contraseña**, por usar `basePackages` + `assignableTypes` juntos
  (Spring los combina con OR, no AND). Se dejó solo `assignableTypes` apuntando a
  `PasswordRecoveryController`.
- **Pantalla en blanco genérica ("Whitelabel Error Page") en cualquier error.**
  Se agregó [`templates/error.html`](src/main/resources/templates/error.html), que
  Spring Boot usa automáticamente para cualquier error no manejado en toda la app
  (400, 404, 500, etc.), mostrando un mensaje entendible en vez de la página en blanco.
- **Bug real: registrar un usuario siempre daba 400**, sin importar qué se escribiera.
  Causa: el objeto del formulario se llamaba `"usuario"` (`@ModelAttribute("usuario")`)
  y el propio `Usuario` tiene un campo que también se llama `usuario` (el nombre de
  usuario). Cuando el nombre del campo coincide con el nombre del objeto completo,
  Spring intenta convertir ese texto al objeto `Usuario` entero en vez de asignarlo
  solo al campo, y revienta. Se renombró el atributo del modelo a `"usuarioForm"` en
  [`UsuarioController`](src/main/java/com/evaluacion01/tecsup/controller/UsuarioController.java)
  y en [`formulario.html`](src/main/resources/templates/formulario.html).
- **Guardar un usuario/correo/DNI duplicado tiraba un error 500 con el SQL crudo.**
  Ahora `UsuarioController.guardar()` atrapa `DataIntegrityViolationException` y
  muestra un mensaje claro ("Ya existe un usuario con ese nombre de usuario", etc.)
  en vez del stacktrace de la base de datos.
- **`data.sql` se ejecutaba en cada reinicio y dejaba roles/ids "sueltos".**
  Con `spring.sql.init.mode=always`, Spring corre `data.sql` cada vez que arranca la
  app. Como sus `INSERT IGNORE` se intentan en cada arranque (aunque el dato ya
  exista), MySQL igual consume un número de `AUTO_INCREMENT` por cada intento, y con
  muchos reinicios (como durante esta depuración) los ids terminan con saltos raros
  (ids en 12, 15, etc. en vez de seguir la secuencia). Además esos roles de ejemplo
  ("ADMIN", "usuario") quedaban mezclados con los roles reales creados desde la app.
  Se renombró el archivo a `data.sql.example` (ya no se ejecuta solo) — sirve como
  plantilla de referencia para poblar una base de datos nueva y vacía a mano, no se
  corre automáticamente.

## Instalación nueva

### Requisitos previos

- JDK 17 con `java` y `javac` disponibles; `JAVA_HOME` debe apuntar al JDK si se configura.
- MySQL accesible en `localhost:3306`, preferentemente MySQL 8.0 o superior.
- Cliente `mysql` o una herramienta SQL como SQLyog o MySQL Workbench.
- Internet para descargar Maven, dependencias y recursos CDN de las vistas.

El entorno local de desarrollo utilizó XAMPP. Algunas instalaciones incluyen MariaDB
o versiones antiguas: no se garantiza compatibilidad con todas ellas y Hibernate puede
mostrar advertencias sobre la versión del motor. No es necesario instalar Maven
por separado porque el proyecto incluye su wrapper.

Comprueba el JDK y ejecuta los comandos siguientes desde la raíz del proyecto:

```text
java -version
javac -version
```

### Preparar la base

Estas instrucciones son para una base nueva y no incluyen comandos de borrado.
Si `bd_hospital` ya contiene datos, consulta primero la sección de bases anteriores.
El usuario SQL necesita permisos para crear el esquema y operar sobre sus tablas.

Abre el cliente desde la raíz del proyecto; sustituye `root` por tu usuario local:

```text
mysql --default-character-set=utf8mb4 -u root -p
```

Si usas XAMPP y el cliente no está en `PATH`, en PowerShell puedes abrirlo así:

```powershell
& "C:\xampp\mysql\bin\mysql.exe" --default-character-set=utf8mb4 -u root -p
```

Dentro del cliente, crea la base y carga el esquema antes de importar los datos:

```sql
CREATE DATABASE IF NOT EXISTS bd_hospital
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE bd_hospital;
SOURCE src/main/resources/schema.sql;
SOURCE src/main/resources/data.sql.example;
```

La semilla actual omite el área de la cuenta inicial. Como ajuste manual de esta
Prueba 1, ejecuta después de importarla:

```sql
UPDATE usuarios u
JOIN roles r ON r.id_rol = u.id_rol
SET u.area = r.area
WHERE u.usuario = 'admin';
```

Comprueba los datos iniciales:

```sql
SELECT COUNT(*) AS total_roles FROM roles;
SELECT COUNT(*) AS total_permisos FROM permisos;
SELECT usuario, area FROM usuarios;
```

En una instalación nueva deben existir 13 roles, 5 permisos y el usuario `admin`
en Administración. Con SQLyog o Workbench, ejecuta el contenido de los dos archivos
en ese mismo orden dentro de `bd_hospital`; `SOURCE` es un comando del cliente
`mysql`, no SQL estándar.

### Configurar y arrancar

Las credenciales se leen de `DB_USERNAME` y `DB_PASSWORD`. Los valores locales por
defecto son `root` y contraseña vacía. Utiliza variables de entorno y no escribas
contraseñas reales en archivos versionados.

Windows PowerShell:

```powershell
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "tu_password_local"
.\mvnw.cmd spring-boot:run
```

Linux/macOS:

```bash
export DB_USERNAME="root"
export DB_PASSWORD="tu_password_local"
sh ./mvnw spring-boot:run
```

Usa tu contraseña real en el entorno de la terminal; si tu instalación local no tiene
contraseña, asigna una cadena vacía. El wrapper Unix se invoca mediante `sh` porque
su permiso ejecutable todavía no está registrado en Git.

Abre `http://localhost:8080/login` e ingresa con `admin` / `admin123`.
Esta cuenta es exclusivamente de demostración. Cambia la contraseña antes de
compartir una instancia y no reutilices esa credencial en otros sistemas.

La aplicación ejecuta `schema.sql` al arrancar, pero no importa `data.sql.example`.
La creación automática de la base mediante la URL JDBC solo funciona si el usuario
SQL tiene permisos suficientes; los pasos anteriores evitan depender de ella.

### Recuperación de contraseña

Sin SMTP configurado, el token se imprime en la consola del servidor y dura 15 minutos.
Este modo sirve únicamente para la demostración local. Para enviar correo real,
configura el bloque SMTP comentado en `application.properties` usando las variables
`MAIL_USERNAME` y `MAIL_PASSWORD`. No publiques logs que contengan tokens.
El mensaje de la vista todavía menciona la consola aunque SMTP esté habilitado.

## Bases anteriores

`schema.sql` usa `CREATE TABLE IF NOT EXISTS`: no actualiza tablas ya existentes.
No hay migraciones automáticas en esta entrega. Una base de la versión anterior puede
carecer de `roles.area`, `usuarios.reset_token` o `usuarios.reset_token_expiry`.

Antes de actualizar una base en uso, haz un respaldo y compara su estructura con
el esquema actual. Incorporar `roles.area` exige clasificar los roles existentes y
reconciliar las áreas de sus usuarios antes de imponer la restricción obligatoria.
Las contraseñas antiguas en texto plano tampoco son compatibles con BCrypt.
No borres una base con datos reales ni importes la semilla como sustituto de una
migración. El procedimiento de actualización se mejorará en futuras entregas.

## Verificación

En la revisión del 30 de septiembre de 2026 se ejecutó:

```powershell
.\mvnw.cmd clean package -DskipTests
```

La compilación y el empaquetado finalizaron correctamente. El JAR nuevo incluye los
servicios, plantillas y CSS actuales, y no contiene el antiguo `data.sql` automático.
Esta ejecución omitió pruebas y no valida los flujos de negocio ni el renderizado
de todas las vistas. El entorno de verificación utilizó JDK 25 con objetivo Java 17;
queda pendiente verificar la ejecución específicamente con JDK 17.

Para ejecutar las pruebas en Windows:

```powershell
.\mvnw.cmd test
```

En Linux/macOS utiliza `sh ./mvnw test`. Las pruebas usan el perfil `test` y una base
H2 en memoria, sin conectar a MySQL ni importar la semilla. Se incluyen:
- `AutorizacionServiceTest`: unión de permisos, roles adicionales, compatibilidad
  del principal y rechazo de identidades obsoletas, inexistentes o inactivas.
- `AutorizacionIntegrationTests`: peticiones reales mediante MockMvc, renderizado
  de formularios, CSRF, autoasignación de Administrador, cuentas protegidas,
  permisos superiores, binding seguro, último administrador y cambios de permisos.
- `TecsupApplicationTests`: carga del contexto con la misma configuración aislada.

Para compilar y empaquetar ejecutando también las pruebas:

```powershell
.\mvnw.cmd package
```

En la verificación de RF-ROL-03/04 del 8 de octubre de 2026, este comando finalizó
con `BUILD SUCCESS`: **42 pruebas, sin fallos ni pruebas omitidas**, usando JDK 21
con objetivo Java 17 y H2 en memoria.

Estas pruebas no sustituyen una verificación contra MySQL ni validan SMTP,
auditoría o cambios administrativos concurrentes. Sigue pendiente verificar
específicamente la ejecución con JDK 17.

## Pendientes posteriores

Los pendientes de sesiones, estado inactivo, último administrador y escalada de
privilegios indicados en la Prueba 1 se abordaron en RF-USR-03 y RF-ROL-03/04.
Permanecen pendientes:

- Verificar y reforzar la consistencia ante cambios administrativos concurrentes.
- Mantener consistentes las áreas de usuarios al cambiar el área de un rol.
- Incluir el área del administrador en la semilla, sin necesitar el ajuste manual.
- Reforzar validaciones de servidor, normalizar DNI vacíos y manejar referencias inválidas.
- Rotar la sesión tras el login, cambiar el cierre de sesión GET a POST y separar configuración local
  de producción para no exponer trazas ni mensajes internos.
- Reforzar recuperación de contraseña, límites de solicitudes e invalidación de tokens.
- Incorporar migraciones, cobertura de otros flujos y módulos hospitalarios adicionales.

## Antes de publicar

Revisa tanto el directorio de trabajo como el contenido preparado para commit.
En la revisión se detectó que el índice conservaba el antiguo `data.sql` y faltaban
archivos nuevos por incorporar. Su eliminación y los nuevos servicios, plantillas,
CSS y semilla deben quedar incluidos en el mismo snapshot del proyecto.
No publiques credenciales reales, tokens, dumps de datos personales ni archivos de
`target/` o del IDE. La publicación en GitHub no implica que esta demo sea apta para
desplegarla en producción.
