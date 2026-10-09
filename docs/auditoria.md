# RF-AUD-01 y RF-AUD-02 — Auditoría hospitalaria

## Persistencia y seguridad

- `auditoria_logs` conserva fecha UTC, ejecutor e ID de cuenta, módulo, acción,
  entidad afectada, resultado y metadatos HTTP (IP directa, método y ruta sin query).
- No se registran cuerpos, parámetros, contraseñas, hashes, cookies ni tokens.
  Los detalles de los eventos se construyen con datos permitidos explícitamente.
- Los campos son de solo inserción. No hay rutas para editar o borrar el historial.
  La identidad se conserva como instantánea, sin FK que borre eventos al eliminar usuarios.
- Un evento exitoso comparte la transacción de la operación crítica. Si alguna de
  las dos escrituras falla, ambas se revierten: no se registra un éxito falso.
- Los fallos y accesos denegados se guardan en una transacción independiente.
  Si la base no está disponible, se informa el problema al log del servidor sin
  datos sensibles y se conserva el error original. No se garantiza registrar
  eventos mientras la base esté caída; requiere supervisión operativa.
- La IP no se obtiene de `X-Forwarded-For`: ese encabezado puede ser falsificado
  si no existe una configuración de proxies de confianza.
- El contexto HTTP se limpia en `finally`, también al lanzar una excepción.

El esquema crea la tabla de forma aditiva (`CREATE TABLE IF NOT EXISTS`), sin
eliminar datos existentes. No migra automáticamente esquemas antiguos incompatibles.
Antes de aplicar SQL a una base real, haz un respaldo y revisa su estructura.

## Verificación

Las pruebas utilizan exclusivamente H2 en memoria y el perfil `test`:

```powershell
.\mvnw.cmd -B verify "-Dspring.profiles.active=test"
```

La primera entrega verifica persistencia, rollback, conservación de intentos
fallidos, límites de campos, fecha UTC y limpieza del contexto. La conexión de
eventos a los servicios y la consulta administrativa se incorporan en las siguientes entregas.
