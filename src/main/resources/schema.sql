CREATE TABLE IF NOT EXISTS roles (
    id_rol INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL UNIQUE,
    descripcion VARCHAR(255),
    area VARCHAR(100) NOT NULL,
    estado BOOLEAN NOT NULL DEFAULT TRUE
);

-- Bases creadas antes del estado de roles: agrega roles.estado solo si falta.
-- Se usa una sentencia preparada porque ADD COLUMN IF NOT EXISTS no existe en MySQL.
SET @falta_estado_rol = (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'roles' AND COLUMN_NAME = 'estado');
SET @sql_estado_rol = IF(@falta_estado_rol,
    'ALTER TABLE roles ADD COLUMN estado BOOLEAN NOT NULL DEFAULT TRUE', 'SELECT 1');
PREPARE stmt_estado_rol FROM @sql_estado_rol;
EXECUTE stmt_estado_rol;
DEALLOCATE PREPARE stmt_estado_rol;

-- Roles base del sistema: deben existir siempre, aunque no se importe la semilla.
INSERT IGNORE INTO roles (nombre, descripcion, area) VALUES
    ('Administrador', 'Gestión total del sistema, usuarios, roles y permisos', 'Administración'),
    ('Médico', 'Historias clínicas, consultas y diagnósticos', 'Medicina'),
    ('Recepcionista', 'Atención y registro de pacientes', 'Recepción');

CREATE TABLE IF NOT EXISTS permisos (
    id_permiso INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL,
    modulo VARCHAR(50) NOT NULL,
    CONSTRAINT uq_permiso_modulo UNIQUE (nombre, modulo)
);

CREATE TABLE IF NOT EXISTS rol_permisos (
    id_rol INT NOT NULL,
    id_permiso INT NOT NULL,
    PRIMARY KEY (id_rol, id_permiso),
    CONSTRAINT fk_rolpermiso_rol FOREIGN KEY (id_rol) REFERENCES roles (id_rol) ON DELETE CASCADE,
    CONSTRAINT fk_rolpermiso_permiso FOREIGN KEY (id_permiso) REFERENCES permisos (id_permiso) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS usuarios (
    id_usuario BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombres VARCHAR(100) NOT NULL,
    apellidos VARCHAR(100) NOT NULL,
    dni VARCHAR(15) UNIQUE,
    correo VARCHAR(100) NOT NULL UNIQUE,
    telefono VARCHAR(20),
    usuario VARCHAR(50) NOT NULL UNIQUE,
    contrasena VARCHAR(255) NOT NULL,
    area VARCHAR(100),
    estado BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_registro DATETIME,
    ultimo_acceso DATETIME,
    id_rol INT NOT NULL,
    reset_token VARCHAR(255),
    reset_token_expiry DATETIME,
    CONSTRAINT fk_usuario_rol FOREIGN KEY (id_rol) REFERENCES roles (id_rol)
);

-- RF-USR-04: un usuario puede tener varios roles. usuarios.id_rol se mantiene como rol principal.
CREATE TABLE IF NOT EXISTS usuario_roles (
    id_usuario BIGINT NOT NULL,
    id_rol INT NOT NULL,
    PRIMARY KEY (id_usuario, id_rol),
    CONSTRAINT fk_usuariorol_usuario FOREIGN KEY (id_usuario) REFERENCES usuarios (id_usuario) ON DELETE CASCADE,
    CONSTRAINT fk_usuariorol_rol FOREIGN KEY (id_rol) REFERENCES roles (id_rol) ON DELETE CASCADE
);

-- Usuarios creados antes de RF-USR-04: su rol principal pasa también a usuario_roles.
INSERT IGNORE INTO usuario_roles (id_usuario, id_rol)
SELECT id_usuario, id_rol FROM usuarios;

-- RF-AUD-01: historial persistente; no borra registros ni depende de cuentas existentes.
CREATE TABLE IF NOT EXISTS auditoria_logs (
    id_auditoria BIGINT AUTO_INCREMENT PRIMARY KEY,
    fecha_hora DATETIME(6) NOT NULL,
    id_usuario_ejecutor BIGINT,
    usuario_ejecutor VARCHAR(100) NOT NULL,
    modulo VARCHAR(20) NOT NULL,
    accion VARCHAR(50) NOT NULL,
    entidad VARCHAR(50),
    id_entidad BIGINT,
    detalle VARCHAR(1000),
    ip VARCHAR(45),
    metodo_http VARCHAR(10),
    ruta VARCHAR(255),
    resultado VARCHAR(10) NOT NULL,
    INDEX idx_auditoria_fecha (fecha_hora, id_auditoria),
    INDEX idx_auditoria_modulo_fecha (modulo, fecha_hora),
    INDEX idx_auditoria_usuario_fecha (usuario_ejecutor, fecha_hora)
);

-- Extensión clínica: los pacientes no son cuentas de acceso.
CREATE TABLE IF NOT EXISTS pacientes (
    id_paciente BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombres VARCHAR(100) NOT NULL,
    apellidos VARCHAR(100) NOT NULL,
    dni VARCHAR(8) NOT NULL UNIQUE,
    fecha_nacimiento DATE NOT NULL,
    telefono VARCHAR(20)
);

-- El servidor determina profesional y fecha. No se elimina historia en cascada.
CREATE TABLE IF NOT EXISTS atenciones (
    id_atencion BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_paciente BIGINT NOT NULL,
    id_medico BIGINT NOT NULL,
    fecha_hora DATETIME(6) NOT NULL,
    motivo VARCHAR(250) NOT NULL,
    diagnostico VARCHAR(2000) NOT NULL,
    indicaciones VARCHAR(2000),
    CONSTRAINT fk_atencion_paciente FOREIGN KEY (id_paciente) REFERENCES pacientes (id_paciente),
    CONSTRAINT fk_atencion_medico FOREIGN KEY (id_medico) REFERENCES usuarios (id_usuario),
    INDEX idx_atencion_paciente_fecha (id_paciente, fecha_hora, id_atencion),
    INDEX idx_atencion_medico_fecha (id_medico, fecha_hora, id_atencion)
);

-- RF-CIT-01: citas. Recepción las programa; no se eliminan, solo cambian de estado.
CREATE TABLE IF NOT EXISTS citas (
    id_cita BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_paciente BIGINT NOT NULL,
    id_medico BIGINT NOT NULL,
    id_registrado_por BIGINT NOT NULL,
    fecha_hora DATETIME(6) NOT NULL,
    motivo VARCHAR(250) NOT NULL,
    estado VARCHAR(15) NOT NULL,
    CONSTRAINT fk_cita_paciente FOREIGN KEY (id_paciente) REFERENCES pacientes (id_paciente),
    CONSTRAINT fk_cita_medico FOREIGN KEY (id_medico) REFERENCES usuarios (id_usuario),
    CONSTRAINT fk_cita_registrado_por FOREIGN KEY (id_registrado_por) REFERENCES usuarios (id_usuario),
    INDEX idx_cita_fecha (fecha_hora, id_cita),
    INDEX idx_cita_medico_fecha (id_medico, fecha_hora)
);

-- Acceso de Recepción a Pacientes y Citas (Evaluación 02, P5) y de los médicos a sus citas.
-- Se aplica UNA sola vez: solo mientras no exista ningún permiso del módulo 'citas'.
-- Después no vuelve a asignar nada, así que lo que un Administrador revoque se respeta.
SET @primera_vez_citas = (SELECT COUNT(*) = 0 FROM permisos WHERE modulo = 'citas');

INSERT IGNORE INTO permisos (nombre, modulo)
SELECT v.nombre, v.modulo FROM (
    SELECT 'VER' AS nombre, 'citas' AS modulo
    UNION ALL SELECT 'CREAR', 'citas'
    UNION ALL SELECT 'EDITAR', 'citas'
    UNION ALL SELECT 'VER', 'pacientes'
    UNION ALL SELECT 'CREAR', 'pacientes'
    UNION ALL SELECT 'EDITAR', 'pacientes'
) v
WHERE @primera_vez_citas;

INSERT IGNORE INTO rol_permisos (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso
FROM roles r
JOIN permisos p ON p.modulo IN ('pacientes', 'citas') AND p.nombre IN ('VER', 'CREAR', 'EDITAR')
WHERE @primera_vez_citas AND r.nombre = 'Recepcionista';

INSERT IGNORE INTO rol_permisos (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso
FROM roles r
JOIN permisos p ON p.modulo = 'citas' AND p.nombre = 'VER'
WHERE @primera_vez_citas AND r.nombre LIKE 'Médico%';
