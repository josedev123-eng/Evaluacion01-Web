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

Las pruebas utilizan exclusivamente H2 en memoria y el perfil `test`, que
deshabilita la autoconfiguración SMTP para no enviar correos externos:

```powershell
.\mvnw.cmd -B verify "-Dspring.profiles.active=test"
```

Las pruebas verifican persistencia, rollback, conservación de intentos fallidos,
límites de campos, fecha UTC y limpieza del contexto.

Verificación del 9 de octubre de 2026: **135 pruebas**, sin fallos, errores ni
pruebas omitidas. No se conectó a MySQL ni se enviaron correos reales.

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

### Recuperación de contraseña

- `SOLICITAR_RECUPERACION`: generación de un token para una cuenta existente.
- `RESTABLECER_CONTRASENA`: cambio de clave y consumo del token válido.
- Ambas operaciones registran éxitos y fallos, incluidos correo inexistente,
  token inválido, reutilizado, expirado o sin fecha, y errores de validación del JSON.
- El ejecutor es `ANONIMO`, porque estas rutas no autentican al solicitante. El
  ID de entidad identifica la cuenta afectada cuando se conoce, sin atribuirle
  falsamente la identidad del solicitante ni copiar su correo a la bitácora.
- No se registran el token, la clave enviada, su hash ni el cuerpo de la petición.
  La escritura de la cuenta y el éxito auditado se revierten juntos si hay rollback.

La recuperación se mantiene en modo simulado para esta entrega: no se configuran
credenciales SMTP ni se prueba envío externo. El token de demostración se consulta
en la consola local; no aparece en la consulta de auditoría.

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

## Consulta administrativa (RF-AUD-02)

Inicia sesión como Administrador y entra a **Auditoría** en el menú o a `/auditoria`.
Funciona si Administrador es el rol principal o uno adicional. La autorización
recarga la cuenta y sus roles de la base: revocar el rol impide nuevas consultas.
Tener un permiso llamado `auditoria / VER` no habilita a un no-administrador.

- Solo lectura; ninguna ruta permite editar, borrar o exportar todos los datos.
- Filtros combinables por módulo, resultado, parte del nombre del ejecutor y fechas.
  `%` y `_` se buscan como texto literal, no como comodines SQL.
- Fechas UTC, desde las 00:00 del día inicial hasta el final del día final incluido.
  Se validan el orden, el formato y los años (1900–9998, por límites de MySQL).
- 25 registros por defecto, hasta 100 por página, ordenados por fecha e ID descendentes.
  Los enlaces de paginación conservan los filtros y la página no puede ser negativa.
- Parámetros inválidos devuelven una vista con error controlado (HTTP 400), sin ejecutar la consulta.
- La vista escapa el texto almacenado, no renderiza HTML del historial y usa `Cache-Control: no-store`.
- El menú se muestra solo a administradores. La URL directa y el servicio también
  verifican autorización. Los accesos denegados quedan registrados sin entregar datos.
