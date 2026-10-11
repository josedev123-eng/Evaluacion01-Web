# Pacientes y Atenciones — extensión académica

Este módulo adicional fue solicitado para que el Médico tenga un flujo hospitalario,
en lugar de consultar cuentas de acceso. No sustituye las entregas de Evaluación 02
ni cambia las evidencias individuales de los compañeros.

Los identificadores **RF-PAC-01**, **RF-PAC-02** y **RF-ATE-01** documentan esta
extensión; no se presentan como requisitos nuevos del enunciado original.

## Alcance y permisos

- Pacientes son personas atendidas, **no usuarios que inician sesión**.
- Administrador: registrar/editar pacientes y revisar atenciones, además de sus
  funciones anteriores. Conserva su acceso total según la política existente.
- Médico: `pacientes:VER`, `atenciones:VER` y `atenciones:CREAR`.
- Las consultas de atenciones se filtran en la BD por el usuario actual; cada
  Médico solo ve sus registros. Administrador puede consultar todos.
- No se incluyen citas, recepción, recetas, medicamentos, adjuntos ni eliminación
  o edición de atenciones. Una atención es un registro firmado que se agrega al historial.
- Las listas muestran hasta 100 resultados, en orden estable.

## Persistencia y seguridad

`Paciente.atenciones` es el inverso `@OneToMany(mappedBy = "paciente")`.
`Atencion.paciente` y `Atencion.medico` son `@ManyToOne` hacia `pacientes` y
`usuarios`. No hay cascadas que borren historia o cuentas; las claves foráneas
impiden eliminar entidades referenciadas. Las asociaciones de las vistas se
precargan mediante `EntityGraph`.

El servidor determina fecha UTC y profesional desde la cuenta activa de la BD.
El formulario no puede modificar esos campos, ni enviar IDs de atención o
asociaciones anidadas. Se verifican permisos también en los servicios, CSRF,
cuentas/roles inactivos, DNI único, fechas de nacimiento y límites de texto.
Las respuestas clínicas usan `Cache-Control: no-store`.

La auditoría registra operaciones, IDs y denegaciones, **no DNI, nombres de
pacientes, motivos, diagnósticos ni indicaciones**. Sus nuevos módulos usan los
VARCHAR ya existentes. Los errores no muestran SQL, parámetros o trazas.

Usar exclusivamente pacientes y notas ficticias: es una demo académica, no un
sistema clínico listo para producción ni una garantía de cumplimiento sanitario.

## Verificación del backend

El 11 de octubre de 2026, `ClinicaBackendIntegrationTests` pasó sus **21 pruebas**
con H2 en memoria: relaciones físicas, identidad/fecha del servidor, aislamiento
por profesional, permisos, cuentas/roles inactivos, validaciones, binding seguro,
CSRF, rutas sin sesión y auditoría sin textos clínicos. La verificación de las
pantallas y del servidor SQL se documentará al completar esos pasos.
