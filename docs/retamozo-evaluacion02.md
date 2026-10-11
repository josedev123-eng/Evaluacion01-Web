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
