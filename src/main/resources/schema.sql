CREATE TABLE IF NOT EXISTS roles (
    id_rol INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL UNIQUE,
    descripcion VARCHAR(255),
    area VARCHAR(100) NOT NULL
);

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
