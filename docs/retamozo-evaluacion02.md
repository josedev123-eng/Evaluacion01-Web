# Retamozo — Evaluación 02, preguntas 1, 4 y 5

## Base integrada

Se integra `feature/edu-req-01-02` (`8efb6ed`) sobre el historial original de
Retamozo. Ese commit ya incluye José (`a616873`), Sovero (`3845613`) y las
entregas anteriores de Retamozo (`e40a9ba`). No se reemplaza ni reescribe ninguna rama.

La integración conserva la vista y las pruebas de auditoría perdidas en la
entrega recibida. Corrige los mensajes con codificación dañada y evita propagar
`PERSIST`/`MERGE` desde usuarios hacia roles compartidos del catálogo. La tabla
`usuario_roles` se sigue actualizando al guardar la asociación, sin esas cascadas.

Los métodos adicionales de Edu se mantienen, pero requieren un operador activo
con permisos y conservan las restricciones del rol principal, cuentas protegidas,
área, roles inactivos y último administrador. Las firmas antiguas sin operador
rechazan la modificación; no autentican ni autorizan automáticamente al llamador.

## Verificación

```powershell
.\mvnw.cmd -B clean verify "-Dspring.profiles.active=test"
```

El perfil `test` usa H2 en memoria, no ejecuta el SQL de MySQL y deshabilita SMTP.
La recuperación continúa simulada. La relación inversa y el frontend se documentan
abajo; cada compañero conserva la responsabilidad de su evidencia individual.

Integración verificada el 11 de octubre de 2026: **204 pruebas**, sin fallos,
errores ni pruebas omitidas. No se conectó a MySQL ni a SMTP real.

Suite completa tras la entrega de frontend: **230 pruebas**, sin fallos, errores
ni pruebas omitidas, ejecutadas con `clean verify` el mismo día y el perfil `test`.
Se repitió la suite después de los ajustes detectados en navegador, con el mismo
resultado. Incluye 26 pruebas de relación inversa, vistas y navegación de Leonel,
además de ocho pruebas nuevas de integración segura del multirrol de Edu.

## Pregunta 1 — Permiso/Rol, lado inverso (RF-ROL-02)

- `Permiso.roles`: `@ManyToMany(mappedBy = "permisos")`, carga LAZY y sin cascadas.
- `Rol.permisos` mantiene la propiedad de la relación y escribe `rol_permisos`.
  No se agregan tablas ni se duplica la clave foránea.
- En **Roles → Permisos** se consultan los roles asociados a cada permiso,
  incluidos los inactivos identificados como tales. La consulta precarga los
  roles con un `EntityGraph`, también fuera de una sesión de persistencia abierta.
- Guardar la selección asigna/revoca filas de `rol_permisos`; no borra roles ni
  permisos del catálogo. Cambiar el lado inverso en memoria no modifica la tabla.
- La pantalla sigue reservada al Administrador y el POST conserva CSRF.

Para la evidencia individual: muestra `Permiso.java`, `Rol.java`, las claves
foráneas de `rol_permisos` y la lista de roles asociados antes y después de guardar.
Sobre la base MySQL de pruebas autorizada, estas consultas son solo de lectura:

```sql
SHOW CREATE TABLE rol_permisos;
SELECT p.id_permiso, p.modulo, p.nombre AS permiso,
       r.id_rol, r.nombre AS rol, r.estado
FROM permisos p
LEFT JOIN rol_permisos rp ON rp.id_permiso = p.id_permiso
LEFT JOIN roles r ON r.id_rol = rp.id_rol
ORDER BY p.modulo, p.nombre, r.nombre;
```

## Pregunta 4 — Gestión visual del estado de roles (RF-ROL-01)

En `/roles` se ve el estado actual y, solo para administradores, el botón de
activar/desactivar. La acción pide confirmación, usa POST con CSRF y envía el
estado deseado explícitamente a `/{id}/estado`. No alterna estados mediante GET.
El Administrador aparece como reservado, sin botón para desactivarlo; el backend
sigue rechazando una petición manipulada. Los perfiles de consulta no ven botones
de edición, asignación de permisos ni cambio de estado.

Los mensajes de éxito/error se muestran de forma accesible. Si faltan roles base,
el listado lo informa sin insertar datos desde la vista. Editar un rol inactivo
muestra un aviso y no lo reactiva. No se agregan nuevos endpoints ni se reemplazan
las reglas de José, Sovero o Edu.

## Pregunta 4 — Formularios de usuarios (RF-USR-01 a RF-USR-05)

`/usuarios` conserva registro, listado/búsqueda, edición, roles y cambio de estado.
Los controles incluyen etiquetas vinculadas, límites y formatos coherentes con
el backend; no muestran la contraseña ni el hash de una cuenta editada. La clave
se deja vacía para conservarla al editar y exige al menos seis caracteres si se ingresa.

La selección se filtra por área y distingue roles principal/adicionales. No se
ofrecen roles inactivos nuevos, pero se identifican los ya asignados para poder
conservarlos o retirarlos. Un operador editando su propia cuenta no tiene controles
habilitados para cambiar área/roles: los campos ocultos conservan la selección
original. Esto no sustituye las validaciones del servicio contra peticiones forjadas.

Los perfiles de consulta no ven formularios de alta ni acciones de edición/estado.
Las cuentas protegidas y la propia cuenta conservan sus restricciones. Los mensajes
se escapan como texto y las bajas/altas explícitas piden confirmación y usan CSRF.
La cabecera de un lector dice **Consulta de usuarios**, no **Registrar usuario**.
En edición propia solo aparecen los roles adicionales ya asignados, bloqueados,
sin mostrar perfiles ajenos ni repetir el principal como adicional.

El listado también muestra la columna **DNI** junto al nombre, coherente con la
búsqueda por DNI ya existente. Si no se registró, aparece **Sin registrar**; el
campo continúa siendo opcional para las cuentas de personal. No se inventan
documentos ni se modifica el esquema o los datos guardados. El mensaje de lista
vacía ocupa las ocho columnas actuales. Las regresiones de
`UsuariosFrontendIntegrationTests` cubren DNI registrado (incluido cero inicial),
consulta/búsqueda por administrador y lector, valores nulos/vacíos y lista vacía.
La verificación completa posterior pasó **277 pruebas sin fallos, errores ni
omisiones**; las 11 de vistas de usuarios incluyen esos tres casos de DNI.

## Pregunta 5 — Navegación según permisos (RF-ROL-03/RF-ROL-04)

Las seis vistas administrativas comparten `fragments/navegacion.html`. El menú
y las tarjetas del dashboard usan los permisos actuales que recarga el backend,
no una copia antigua de la sesión. El módulo actual tiene `aria-current="page"`.
La barra conserva el comportamiento adaptable a pantallas pequeñas.

La redirección administrativa de Sovero se conserva: Administrador → dashboard,
consulta de usuarios → usuarios, consulta de roles → roles, sin módulos → dashboard.
La extensión clínica posterior prioriza Pacientes para quien tiene `pacientes:VER`;
se documenta por separado en [modulo-clinico.md](modulo-clinico.md).
Ocultar enlaces no autoriza nada: se comprueban también URLs directas, formularios
forjados y la revocación de permisos de una sesión anterior.

## Demostración aislada desde el navegador

No modifica MySQL, no envía correos y no agrega cuentas al ejecutable de producción.
Usa una base H2 temporal que se pierde al apagar el proceso:

```powershell
.\scripts\demo-retamozo.ps1
```

Si la política local bloquea scripts, ejecuta
`powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo-retamozo.ps1`.
No cambia la política del equipo. Puedes usar `-Puerto 8096` para otro puerto;
para detener la demostración, pulsa `Ctrl+C` en su terminal.

Abre `http://127.0.0.1:8095/login`. La consola muestra una clave temporal aleatoria
para `demoAdmin`, `demoMedico`, `demoRecepcion` y `demoEditor`. Son cuentas ficticias
de esta ejecución, no credenciales SMTP. Los perfiles no administrativos tienen
solo los permisos asignados en el fixture. La extensión clínica solicitada después
agrega Pacientes y Atenciones; no se anuncia como parte del enunciado original.

Guion de evidencia:

1. Administrador: registrar/editar un usuario y seleccionar sus roles por área.
2. Activar/desactivar otra cuenta; comprobar su estado en el listado.
3. Crear/editar un rol; desactivarlo y reactivarlo desde `/roles`.
4. Asignar/revocar permisos y observar la relación inversa en la misma pantalla.
5. Consultar la bitácora y comprobar los IDs y operaciones de esos cambios.
6. En la demo actual, entrar como Médico: consultar pacientes y registrar una
   atención, sin enlaces de administración; probar también una URL administrativa.
7. Entrar como Editor y editar su propia cuenta: sus roles y área quedan bloqueados.

Comprobaciones originales del 11 de octubre sobre H2 temporal, **anteriores a la
extensión clínica** (en ese fixture el Médico recibió `usuarios:VER` para probar
una vista de solo consulta):

| Flujo | Resultado observado |
| --- | --- |
| Registrar y editar una cuenta con principal y adicional | Aparece en el listado y conserva ambas asociaciones |
| Desactivar y reactivar esa cuenta | Cambian estado, botón y mensaje de resultado |
| Crear/editar un rol y cambiar su estado | Datos y estado se reflejan en el listado, con confirmación |
| Asignar/revocar `usuarios:VER` | El rol aparece/desaparece en `Permiso.roles`, sin borrar el catálogo |
| Consultar auditoría del administrador | Aparecen IDs, estados y asignaciones/revocaciones, sin claves ni tokens |
| Médico con permiso de consulta | Redirige a usuarios, sin formularios ni acciones de administración |
| Médico abre `/auditoria` directamente | Redirige al dashboard con mensaje de acceso denegado |

La compilación de la demo incluye clases de prueba solo en el proceso local.
El JAR de producción no contiene `RetamozoDemoConfig` ni la dependencia H2.
Estas comprobaciones no equivalen a verificar el esquema real con MySQL.

Para MySQL 8: usar una base de pruebas autorizada y respaldada, aplicar el esquema
del backend y repetir el guion. En la primera revisión del 11 de octubre el servidor
local no atendía en puerto 3306. La prueba posterior con el servidor XAMPP encendido
se documenta a continuación; no se presenta MariaDB como si fuera MySQL 8.

## Prueba del servidor SQL local — XAMPP

Al solicitar una nueva prueba el 11 de octubre, el puerto 3306 ya estaba activo.
La consulta `SELECT VERSION(), @@version_comment` identificó **MariaDB 10.4.32**,
no MySQL 8. Se creó exclusivamente la base
`ev02_retamozo_prueba_20261011_130017_6d57a271`; no se ejecutaron cambios en
`bd_hospital` ni en otras bases existentes.

El arranque de prueba utiliza `ddl-auto=validate`, el esquema SQL del proyecto
antes de JPA y la autoconfiguración SMTP deshabilitada. La primera validación
detectó que Hibernate 6 esperaba ENUM nativo para `AuditoriaLog.modulo` y
`resultado`, mientras que `schema.sql` define VARCHAR. Se añade
`@JdbcTypeCode(SqlTypes.VARCHAR)` a ambos campos, manteniendo `EnumType.STRING`,
los valores del dominio y las columnas existentes; no se requiere migración.

La semilla manual de esta base de pruebas contiene **14 roles, 6 permisos y una
cuenta administrativa ficticia**. Su área se completó con el área del rol dentro
de la misma base, como indica la instalación del README. Ninguna clave ni token
se incluye en los resultados de auditoría o consultas de evidencia.

Tras el ajuste, `mvnw.cmd -B verify "-Dspring.profiles.active=test"` finalizó con
**231 pruebas, cero fallos, errores u omisiones**, incluido el nuevo caso que
impide retirar el mapeo VARCHAR de esos enums. La inspección de la base separada
confirmó `modulo VARCHAR(20)`, `resultado VARCHAR(10)` y las cinco claves foráneas
de `usuarios.id_rol`, `usuario_roles` y `rol_permisos`.

La aplicación arrancó correctamente con esa validación estricta y se comprobaron
desde navegador y SQL las siguientes operaciones sobre la base separada:

| Comprobación | Resultado |
| --- | --- |
| Login administrativo | Acceso al dashboard |
| Registro multirrol | `pruebaRetamozoSql` conserva principal Médico y adicional Médico general en `usuario_roles` |
| Edición con clave vacía | Cambian los datos; sigue funcionando la clave anterior y se mantiene BCrypt |
| Activar/desactivar cuenta y rol | Los estados cambian en la vista y la persistencia; Administrador aparece reservado |
| Asignar, revocar y reasignar `usuarios:VER` | La consulta inversa refleja cada cambio y el catálogo se conserva |
| Auditoría administrativa | Se consultan éxitos de usuarios, roles y permisos guardados en las columnas VARCHAR |
| Usuario de consulta | Login redirige a usuarios, sin alta, edición ni controles de estado |
| URL de auditoría no autorizada | Redirección al dashboard; se persiste el evento DENEGADO |
| POST de estado sin CSRF | HTTP 403, sin modificar la cuenta |
| Solicitud de recuperación simulada | HTTP 200 y token únicamente en la consola, con SMTP deshabilitado |
| Restablecimiento y reutilización | Éxito inicial; token y vencimiento quedan NULL; un segundo uso devuelve HTTP 400 |

La base separada se conserva para revisar la evidencia; no incluye usuarios reales.
Los logs temporales con el token simulado **no se publican**. Con el dialecto MySQL
predeterminado Hibernate advierte sobre la versión anunciada por MariaDB; las
comprobaciones realizadas son del motor y versión indicados, no una garantía de
compatibilidad universal. La ejecución específicamente con MySQL 8 sigue pendiente.

La ampliación posterior de esa misma base aislada con tablas y permisos clínicos
se documenta en [modulo-clinico.md](modulo-clinico.md). El permiso demostrativo
`Médico → usuarios:VER` se retiró allí; las verificaciones originales anteriores
se conservan como historial, no como el comportamiento clínico actual.
