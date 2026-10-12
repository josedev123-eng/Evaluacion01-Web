# Pacientes y Atenciones — extensión académica

**Responsable de esta extensión:** Leonel Mathias Retamozo De la Cruz.

Este módulo adicional fue solicitado para que el Médico tenga un flujo hospitalario,
en lugar de consultar cuentas de acceso. No sustituye las entregas de Evaluación 02
ni cambia las evidencias individuales de los compañeros.

Los identificadores **RF-PAC-01**, **RF-PAC-02** y **RF-ATE-01** documentan esta
extensión; no se presentan como requisitos nuevos del enunciado original.

- **RF-PAC-01:** registro y edición de datos demográficos con validaciones.
- **RF-PAC-02:** búsqueda y ficha de pacientes según permisos.
- **RF-ATE-01:** registro de una atención con profesional/fecha del servidor y
  consulta autorizada de notas propias (todas para Administrador).

## Alcance y permisos

- Pacientes son personas atendidas, **no usuarios que inician sesión**.
- Administrador: registrar/editar pacientes y revisar atenciones, además de sus
  funciones anteriores. Conserva su acceso total según la política existente.
- Médico: `pacientes:VER`, `atenciones:VER` y `atenciones:CREAR`.
- Las consultas de atenciones se filtran en la BD por el usuario actual; cada
  Médico solo ve sus registros. Administrador puede consultar todos.
- No se incluyen citas, recepción, recetas, medicamentos, adjuntos ni eliminación
  o edición de atenciones. Una atención conserva la cuenta autora y se agrega al
  historial; no implementa una firma digital criptográfica.
- Las listas muestran hasta 100 resultados, en orden estable.

## Persistencia y seguridad

`Paciente.atenciones` es el inverso `@OneToMany(mappedBy = "paciente")`.
`Atencion.paciente` y `Atencion.medico` son `@ManyToOne` hacia `pacientes` y
`usuarios`. No hay cascadas que borren historia o cuentas; las claves foráneas
impiden eliminar entidades referenciadas. Las asociaciones de las vistas se
precargan mediante `EntityGraph`.

El servidor determina fecha UTC y profesional desde la cuenta activa de la BD.
El formulario no puede modificar esos campos, ni asignar IDs de atención o
asociaciones anidadas. Se verifican permisos también en los servicios, CSRF,
cuentas/roles inactivos, DNI único, fechas de nacimiento y límites de texto.
Las respuestas clínicas usan `Cache-Control: no-store`.

La auditoría registra operaciones, IDs y denegaciones, **no DNI, nombres de
pacientes, motivos, diagnósticos ni indicaciones**. Sus nuevos módulos usan los
VARCHAR ya existentes. Los errores no muestran SQL, parámetros o trazas.

Usar exclusivamente pacientes y notas ficticias: es una demo académica, no un
sistema clínico listo para producción ni una garantía de cumplimiento sanitario.

## Habilitar la extensión

En una instalación nueva, importar `schema.sql` y `data.sql.example` como antes.
La semilla ahora incluye **11 permisos**; Médico, Médico general y Médico
especialista reciben los tres permisos clínicos, sin permisos de usuarios.

Para una base anterior, respaldarla y elegirla explícitamente en el cliente SQL;
ejecutar `schema.sql` y, **una sola vez de forma manual**,
`src/main/resources/permisos-clinicos.sql.example`. Este último agrega permisos
y asociaciones médicas sin crear cuentas, borrar registros ni revocar permisos
preexistentes. Si el Médico tenía acceso administrativo otorgado para una demo,
el Administrador puede desmarcarlo en `/roles/{id}/permisos`; no se retiran
permisos personalizados automáticamente. Ninguno de los `.example` se ejecuta
durante el arranque.

El Administrador puede ajustar los permisos desde Roles y conserva acceso total.
Los roles adicionales siguen aportando sus permisos; el nombre/área Medicina no
concede acceso por sí solo. Tras el login, los usuarios con `pacientes:VER` entran
a `/pacientes`; Administrador sigue en `/dashboard` y los lectores de cuentas
conservan `/usuarios`.

La demo H2 `scripts/demo-retamozo.ps1` incluye un paciente ficticio. `demoMedico`
consulta pacientes/registra atenciones, `demoEditor` conserva sus permisos de
usuarios y agrega los clínicos por su rol adicional; `demoRecepcion` no tiene
funciones de recepción implementadas. Las claves temporales solo están en consola.

### Flujo de demostración

1. Administrador: Pacientes → Registrar paciente; ingresar datos ficticios y guardar.
2. Médico: iniciar sesión → buscar por nombre/DNI → Abrir ficha.
3. Completar motivo, diagnóstico e indicaciones y guardar la atención.
4. Revisar Mis atenciones. Otro Médico no ve esa nota; Administrador sí la revisa.
5. Revocar permisos o desactivar el rol: la sesión existente pierde ese acceso.

### Relaciones físicas para comprobar con SQL

```sql
SELECT a.id_atencion, a.id_paciente, a.id_medico, a.fecha_hora,
       u.usuario AS profesional
FROM atenciones a
JOIN pacientes p ON p.id_paciente = a.id_paciente
JOIN usuarios u ON u.id_usuario = a.id_medico;

SELECT CONSTRAINT_NAME, TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'atenciones'
  AND REFERENCED_TABLE_NAME IS NOT NULL;
```

Estas consultas no muestran los textos clínicos ni documentos personales.

## Verificación

El 11 de octubre de 2026, `ClinicaBackendIntegrationTests` pasó sus **21 pruebas**
con H2 en memoria: relaciones físicas, identidad/fecha del servidor, aislamiento
por profesional, permisos, cuentas/roles inactivos, validaciones, binding seguro,
CSRF, rutas sin sesión y auditoría sin textos clínicos.

`ClinicaFrontendIntegrationTests` pasó **20 pruebas** de renderizado, validaciones,
precarga de fechas, navegación, XSS, multirrol, revocación en sesiones existentes,
aislamiento de notas, CSRF y ausencia de controles de gestión para el Médico.
Las **5 pruebas** de navegación compartida también pasaron. La verificación
completa y la del servidor SQL se documentarán al completar esos pasos.
