create database centro_computo;
use centro_computo;

CREATE TABLE usuarios (
    id_usuario INT PRIMARY KEY AUTO_INCREMENT,
    nombre VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100),
    rol ENUM('ADMINISTRADOR', 'TECNICO', 'USUARIO') NOT NULL
);
-- MÓDULO 5: GESTIÓN DE BASE DE DATOS
-- Script para crear tablas necesarias

-- Tabla de backups
CREATE TABLE IF NOT EXISTS backups (
    id INT AUTO_INCREMENT PRIMARY KEY,
    backup_name VARCHAR(255) NOT NULL,
    source_path VARCHAR(500) NOT NULL,
    backup_path VARCHAR(500) NOT NULL,
    size_bytes BIGINT NOT NULL,
    user_email VARCHAR(255) NOT NULL,
    status ENUM('SUCCESS', 'ERROR') NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_email),
    INDEX idx_status (status),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Tabla de conexiones activas (histórico)
CREATE TABLE IF NOT EXISTS db_connections_log (
    id INT AUTO_INCREMENT PRIMARY KEY,
    connection_id BIGINT,
    user VARCHAR(100),
    host VARCHAR(100),
    db_name VARCHAR(100),
    command VARCHAR(50),
    time_seconds INT,
    state VARCHAR(100),
    info TEXT,
    logged_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_logged_at (logged_at),
    INDEX idx_user (user)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Tabla de estadísticas de rendimiento
CREATE TABLE IF NOT EXISTS db_performance_stats (
    id INT AUTO_INCREMENT PRIMARY KEY,
    metric_name VARCHAR(100) NOT NULL,
    metric_value VARCHAR(255) NOT NULL,
    metric_type ENUM('QUERY', 'CONNECTION', 'MEMORY', 'DISK', 'OTHER') NOT NULL,
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_metric (metric_name),
    INDEX idx_recorded (recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Tabla de consultas ejecutadas
CREATE TABLE IF NOT EXISTS query_executions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    query_text TEXT NOT NULL,
    user_email VARCHAR(255) NOT NULL,
    execution_time_ms BIGINT,
    rows_affected INT,
    status ENUM('SUCCESS', 'ERROR', 'TIMEOUT') NOT NULL,
    error_message TEXT,
    executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_email),
    INDEX idx_status (status),
    INDEX idx_executed (executed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Tabla de respaldos de estructura
CREATE TABLE IF NOT EXISTS structure_backups (
    id INT AUTO_INCREMENT PRIMARY KEY,
    backup_name VARCHAR(255) NOT NULL,
    database_name VARCHAR(100) NOT NULL,
    backup_content LONGTEXT NOT NULL,
    backup_type ENUM('FULL', 'SCHEMA_ONLY', 'DATA_ONLY') NOT NULL,
    user_email VARCHAR(255) NOT NULL,
    size_bytes BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_database (database_name),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Vista para estadísticas rápidas de backups
CREATE OR REPLACE VIEW backup_statistics AS
SELECT
    COUNT(*) as total_backups,
    SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) as successful_backups,
    SUM(CASE WHEN status = 'ERROR' THEN 1 ELSE 0 END) as failed_backups,
    SUM(size_bytes) as total_size_bytes,
    AVG(size_bytes) as avg_size_bytes,
    MAX(created_at) as last_backup_date
FROM backups;

-- Vista para conexiones activas actuales (se actualiza en tiempo real)
CREATE OR REPLACE VIEW current_connections AS
SELECT
    ID as connection_id,
    USER as user,
    HOST as host,
    DB as database_name,
    COMMAND as command,
    TIME as time_seconds,
    STATE as state,
    INFO as info
FROM information_schema.PROCESSLIST
WHERE USER != 'system user'
ORDER BY TIME DESC;

-- Insertar datos de ejemplo para testing (opcional)
-- INSERT INTO backups (backup_name, source_path, backup_path, size_bytes, user_email, status)
-- VALUES ('Test_Backup_20250101', 'C:\\Test', 'C:\\Backups\\Test', 1048576, 'admin@test.com', 'SUCCESS');

-- =====================================================
-- MÓDULO 6: SISTEMA DE TICKETS DE SOPORTE
-- Base de datos: centro_computo
-- =====================================================

-- Tabla principal de tickets
CREATE TABLE IF NOT EXISTS tickets (
    id INT AUTO_INCREMENT PRIMARY KEY,
    ticket_number VARCHAR(20) UNIQUE NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    category ENUM('HARDWARE', 'SOFTWARE', 'RED', 'SEGURIDAD', 'OTRO') DEFAULT 'OTRO',
    priority ENUM('BAJA', 'MEDIA', 'ALTA', 'CRITICA') DEFAULT 'MEDIA',
    status ENUM('ABIERTO', 'EN_PROGRESO', 'PENDIENTE', 'RESUELTO', 'CERRADO') DEFAULT 'ABIERTO',

    -- Usuario que crea el ticket
    created_by_email VARCHAR(255) NOT NULL,
    created_by_name VARCHAR(255),

    -- Técnico asignado
    assigned_to_email VARCHAR(255),
    assigned_to_name VARCHAR(255),

    -- Fechas
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL,
    closed_at TIMESTAMP NULL,

    -- Índices
    INDEX idx_status (status),
    INDEX idx_priority (priority),
    INDEX idx_created_by (created_by_email),
    INDEX idx_assigned_to (assigned_to_email),
    INDEX idx_created_at (created_at)
);

-- Tabla de comentarios de tickets
CREATE TABLE IF NOT EXISTS ticket_comments (
    id INT AUTO_INCREMENT PRIMARY KEY,
    ticket_id INT NOT NULL,
    user_email VARCHAR(255) NOT NULL,
    user_name VARCHAR(255),
    comment TEXT NOT NULL,
    is_internal BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE,
    INDEX idx_ticket_id (ticket_id)
);

-- Tabla de historial de cambios
CREATE TABLE IF NOT EXISTS ticket_history (
    id INT AUTO_INCREMENT PRIMARY KEY,
    ticket_id INT NOT NULL,
    user_email VARCHAR(255) NOT NULL,
    user_name VARCHAR(255),
    action VARCHAR(100) NOT NULL,
    old_value VARCHAR(255),
    new_value VARCHAR(255),
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE,
    INDEX idx_ticket_history (ticket_id)
);