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
