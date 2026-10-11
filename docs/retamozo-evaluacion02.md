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
La recuperación continúa simulada. Las evidencias específicas de la relación
inversa y del frontend se agregan con sus respectivas entregas.

Integración verificada el 11 de octubre de 2026: **204 pruebas**, sin fallos,
errores ni pruebas omitidas. No se conectó a MySQL ni a SMTP real.

Suite completa tras la entrega de frontend: **230 pruebas**, sin fallos, errores
ni pruebas omitidas, ejecutadas con `clean verify` el mismo día y el perfil `test`.

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

## Pregunta 5 — Navegación según permisos (RF-ROL-03/RF-ROL-04)

Las seis vistas administrativas comparten `fragments/navegacion.html`. El menú
y las tarjetas del dashboard usan los permisos actuales que recarga el backend,
no una copia antigua de la sesión. El módulo actual tiene `aria-current="page"`.
La barra conserva el comportamiento adaptable a pantallas pequeñas.

La redirección de Sovero se conserva: Administrador → dashboard, permiso de
consulta de usuarios → usuarios, consulta de roles → roles, sin módulos → dashboard.
Ocultar enlaces no autoriza nada: se comprueban también URLs directas, formularios
forjados y la revocación de permisos de una sesión anterior.

## Demostración aislada desde el navegador

No modifica MySQL, no envía correos y no agrega cuentas al ejecutable de producción.
Usa una base H2 temporal que se pierde al apagar el proceso:

```powershell
.\mvnw.cmd -B spring-boot:run "-Dspring-boot.run.useTestClasspath=true" "-Dspring-boot.run.profiles=test,demo" "-Dspring-boot.run.arguments=--server.port=8095"
```

Abre `http://127.0.0.1:8095/login`. La consola muestra una clave temporal aleatoria
para `demoAdmin`, `demoMedico`, `demoRecepcion` y `demoEditor`. Son cuentas ficticias
de esta ejecución, no credenciales SMTP. Los perfiles no administrativos tienen
solo los permisos asignados en el fixture; los módulos clínicos del ejemplo del
enunciado no se inventan ni se anuncian como implementados.

Guion de evidencia:

1. Administrador: registrar/editar un usuario y seleccionar sus roles por área.
2. Activar/desactivar otra cuenta; comprobar su estado en el listado.
3. Crear/editar un rol; desactivarlo y reactivarlo desde `/roles`.
4. Asignar/revocar permisos y observar la relación inversa en la misma pantalla.
5. Consultar la bitácora y comprobar los IDs y operaciones de esos cambios.
6. Entrar como Médico: consultar usuarios, sin formularios de administración ni
   enlaces a roles/auditoría; probar también una URL administrativa directa.
7. Entrar como Editor y editar su propia cuenta: sus roles y área quedan bloqueados.

Para MySQL real: usar una base de pruebas autorizada y respaldada, aplicar el esquema
del backend y repetir el guion. El 11 de octubre el servidor local no atendía en
puerto 3306, por lo que aún no se afirma verificación con MySQL.
