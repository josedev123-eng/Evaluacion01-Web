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

Las pruebas verifican persistencia, rollback, conservación de intentos fallidos,
límites de campos, fecha UTC y limpieza del contexto.

## Autenticación (RF-AUD-01)

- `LOGIN / EXITO`: cuenta autenticada y actualización de último acceso en la misma
  transacción. Se registra una sola vez en `AuthService`, también fuera de HTTP.
- `LOGIN / FALLO`: datos incompletos o credenciales inválidas. Se conserva el
  identificador intentado (máximo 100 caracteres), nunca la clave enviada.
- `LOGIN / DENEGADO`: cuenta inactiva; no se crea una sesión autenticada.
- `LOGOUT / EXITO`: cierre de una sesión con usuario, antes de invalidarla.
  Un logout anónimo no genera un cierre ficticio de usuario.

Los motivos son códigos definidos por la aplicación, no mensajes de excepciones
que puedan contener SQL, datos personales o secretos.

## Operaciones críticas (RF-AUD-01)

- Usuarios: `CREAR_USUARIO`, `EDITAR_USUARIO`, `ACTIVAR_USUARIO`, `DESACTIVAR_USUARIO`.
  Se informa el ID afectado, los nombres de campos modificados y los IDs de roles
  anteriores y nuevos. No se copian valores de DNI, teléfono, correo o contraseña.
- Roles: `CREAR_ROL`, `EDITAR_ROL`, con el ID y los nombres de campos modificados.
- Permisos: `ASIGNAR_PERMISOS`, incluyendo los IDs anteriores y posteriores del rol.
- Los servicios registran también rechazos en llamadas fuera de HTTP. Los
  controladores registran validaciones y permisos que impiden llegar al servicio,
  sin duplicar los eventos de operaciones que sí lo alcanzaron.
- Los intentos sin sesión, sesiones inactivas y formularios sin CSRF se registran
  como `DENEGADO`. Ninguno produce un evento de modificación exitosa.

Las operaciones conservan todas las protecciones de Sovero. Se ejecuta `flush`
antes de informar éxito para detectar violaciones de integridad dentro de la
operación, y los éxitos se revierten junto con el cambio si la transacción falla.
La consulta administrativa se incorpora en RF-AUD-02.
