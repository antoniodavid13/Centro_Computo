package com.antovdv.centro_computo.service;

import com.antovdv.centro_computo.database;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class DatabaseManagementService {

    /**
     * Obtiene todas las conexiones activas a la base de datos
     */
    public List<Map<String, Object>> getActiveConnections() {
        List<Map<String, Object>> connections = new ArrayList<>();

        String sql = "SELECT ID, USER, HOST, DB, COMMAND, TIME, STATE, INFO " +
                "FROM information_schema.PROCESSLIST " +
                "WHERE USER != 'system user' " +
                "ORDER BY TIME DESC";

        try (Connection conn = database.getConection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> connection = new HashMap<>();
                connection.put("id", rs.getLong("ID"));
                connection.put("user", rs.getString("USER"));
                connection.put("host", rs.getString("HOST"));
                connection.put("database", rs.getString("DB"));
                connection.put("command", rs.getString("COMMAND"));
                connection.put("time", rs.getInt("TIME"));
                connection.put("state", rs.getString("STATE"));
                connection.put("info", rs.getString("INFO"));
                connections.add(connection);
            }

        } catch (SQLException e) {
            System.err.println("Error obteniendo conexiones activas: " + e.getMessage());
        }

        return connections;
    }

    /**
     * Obtiene estadísticas de rendimiento de la base de datos
     */
    public Map<String, Object> getDatabaseStatistics() {
        Map<String, Object> stats = new HashMap<>();

        try (Connection conn = database.getConection()) {

            // Estadísticas de conexiones
            stats.put("activeConnections", getActiveConnectionCount(conn));
            stats.put("maxConnections", getMaxConnections(conn));

            // Estadísticas de consultas
            stats.put("queriesPerSecond", getQueriesPerSecond(conn));
            stats.put("slowQueries", getSlowQueryCount(conn));

            // Estadísticas de base de datos
            stats.put("databaseSize", getDatabaseSize(conn));
            stats.put("tableCount", getTableCount(conn));

            // Estadísticas de memoria
            stats.put("bufferPoolSize", getBufferPoolSize(conn));
            stats.put("cacheHitRatio", getCacheHitRatio(conn));

            // Tiempo de actividad
            stats.put("uptime", getUptime(conn));

            // Guardar estadísticas en la tabla
            savePerformanceStats(stats);

        } catch (SQLException e) {
            System.err.println("Error obteniendo estadísticas: " + e.getMessage());
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    /**
     * Ejecuta una consulta SQL con timeout de seguridad
     */
    public Map<String, Object> executeQueryWithTimeout(String query, String userEmail, int timeoutSeconds) {
        Map<String, Object> result = new HashMap<>();
        long startTime = System.currentTimeMillis();

        try (Connection conn = database.getConection()) {

            // Configurar timeout
            try (Statement stmt = conn.createStatement()) {
                stmt.setQueryTimeout(timeoutSeconds);

                // Determinar tipo de consulta
                String queryUpper = query.trim().toUpperCase();
                boolean isSelect = queryUpper.startsWith("SELECT");

                if (isSelect) {
                    // Consulta SELECT
                    try (ResultSet rs = stmt.executeQuery(query)) {
                        List<Map<String, Object>> rows = new ArrayList<>();
                        ResultSetMetaData metaData = rs.getMetaData();
                        int columnCount = metaData.getColumnCount();

                        // Obtener nombres de columnas
                        List<String> columns = new ArrayList<>();
                        for (int i = 1; i <= columnCount; i++) {
                            columns.add(metaData.getColumnName(i));
                        }

                        // Obtener filas (limitar a 1000 para seguridad)
                        int rowCount = 0;
                        while (rs.next() && rowCount < 1000) {
                            Map<String, Object> row = new HashMap<>();
                            for (int i = 1; i <= columnCount; i++) {
                                row.put(columns.get(i - 1), rs.getObject(i));
                            }
                            rows.add(row);
                            rowCount++;
                        }

                        long executionTime = System.currentTimeMillis() - startTime;

                        result.put("success", true);
                        result.put("type", "SELECT");
                        result.put("columns", columns);
                        result.put("rows", rows);
                        result.put("rowCount", rowCount);
                        result.put("executionTime", executionTime);

                        // Registrar en BD
                        logQueryExecution(query, userEmail, executionTime, rowCount, "SUCCESS", null);
                    }
                } else {
                    // Consulta de modificación (INSERT, UPDATE, DELETE)
                    int rowsAffected = stmt.executeUpdate(query);
                    long executionTime = System.currentTimeMillis() - startTime;

                    result.put("success", true);
                    result.put("type", "UPDATE");
                    result.put("rowsAffected", rowsAffected);
                    result.put("executionTime", executionTime);

                    // Registrar en BD
                    logQueryExecution(query, userEmail, executionTime, rowsAffected, "SUCCESS", null);
                }
            }

        } catch (SQLException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            String status = e.getMessage().contains("timeout") ? "TIMEOUT" : "ERROR";

            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("executionTime", executionTime);

            // Registrar error en BD
            logQueryExecution(query, userEmail, executionTime, 0, status, e.getMessage());
        }

        return result;
    }

    /**
     * Crea un respaldo de la estructura de la base de datos
     */
    public Map<String, Object> backupDatabaseStructure(String databaseName, String backupName, String userEmail) {
        Map<String, Object> result = new HashMap<>();
        StringBuilder backupContent = new StringBuilder();

        try (Connection conn = database.getConection()) {

            // Obtener todas las tablas
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet tables = metaData.getTables(databaseName, null, "%", new String[]{"TABLE"});

            backupContent.append("-- Database Structure Backup\n");
            backupContent.append("-- Database: ").append(databaseName).append("\n");
            backupContent.append("-- Date: ").append(LocalDateTime.now()).append("\n\n");

            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");

                // Obtener CREATE TABLE
                String createTableSQL = getCreateTableStatement(conn, tableName);
                backupContent.append(createTableSQL).append("\n\n");
            }

            // Guardar en base de datos
            String backupContentStr = backupContent.toString();
            long sizeBytes = backupContentStr.getBytes().length;

            String sql = "INSERT INTO structure_backups " +
                    "(backup_name, database_name, backup_content, backup_type, user_email, size_bytes) " +
                    "VALUES (?, ?, ?, 'SCHEMA_ONLY', ?, ?)";

            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, backupName);
                ps.setString(2, databaseName);
                ps.setString(3, backupContentStr);
                ps.setString(4, userEmail);
                ps.setLong(5, sizeBytes);

                int affected = ps.executeUpdate();

                if (affected > 0) {
                    ResultSet generatedKeys = ps.getGeneratedKeys();
                    if (generatedKeys.next()) {
                        result.put("success", true);
                        result.put("backupId", generatedKeys.getInt(1));
                        result.put("backupName", backupName);
                        result.put("size", sizeBytes);
                        result.put("timestamp", LocalDateTime.now().toString());
                    }
                }
            }

        } catch (SQLException e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    // Métodos auxiliares privados

    private int getActiveConnectionCount(Connection conn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE USER != 'system user'";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private int getMaxConnections(Connection conn) throws SQLException {
        String sql = "SHOW VARIABLES LIKE 'max_connections'";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt("Value") : 0;
        }
    }

    private double getQueriesPerSecond(Connection conn) throws SQLException {
        String sql = "SHOW GLOBAL STATUS LIKE 'Questions'";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                long questions = rs.getLong("Value");
                long uptime = getUptime(conn);
                return uptime > 0 ? (double) questions / uptime : 0;
            }
        }
        return 0;
    }

    private long getSlowQueryCount(Connection conn) throws SQLException {
        String sql = "SHOW GLOBAL STATUS LIKE 'Slow_queries'";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong("Value") : 0;
        }
    }

    private long getDatabaseSize(Connection conn) throws SQLException {
        String sql = "SELECT SUM(data_length + index_length) as size " +
                "FROM information_schema.TABLES " +
                "WHERE table_schema = DATABASE()";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong("size") : 0;
        }
    }

    private int getTableCount(Connection conn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.TABLES WHERE table_schema = DATABASE()";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private long getBufferPoolSize(Connection conn) throws SQLException {
        String sql = "SHOW VARIABLES LIKE 'innodb_buffer_pool_size'";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong("Value") : 0;
        }
    }

    private double getCacheHitRatio(Connection conn) throws SQLException {
        try {
            String sql = "SHOW GLOBAL STATUS WHERE Variable_name IN ('Innodb_buffer_pool_read_requests', 'Innodb_buffer_pool_reads')";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {

                long requests = 0, reads = 0;
                while (rs.next()) {
                    String varName = rs.getString("Variable_name");
                    if ("Innodb_buffer_pool_read_requests".equals(varName)) {
                        requests = rs.getLong("Value");
                    } else if ("Innodb_buffer_pool_reads".equals(varName)) {
                        reads = rs.getLong("Value");
                    }
                }

                return requests > 0 ? (1.0 - ((double) reads / requests)) * 100 : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    private long getUptime(Connection conn) throws SQLException {
        String sql = "SHOW GLOBAL STATUS LIKE 'Uptime'";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong("Value") : 0;
        }
    }

    private String getCreateTableStatement(Connection conn, String tableName) throws SQLException {
        String sql = "SHOW CREATE TABLE " + tableName;
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getString(2) + ";";
            }
        }
        return "";
    }

    private void savePerformanceStats(Map<String, Object> stats) {
        String sql = "INSERT INTO db_performance_stats (metric_name, metric_value, metric_type) VALUES (?, ?, ?)";

        try (Connection conn = database.getConection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            for (Map.Entry<String, Object> entry : stats.entrySet()) {
                ps.setString(1, entry.getKey());
                ps.setString(2, String.valueOf(entry.getValue()));
                ps.setString(3, "OTHER");
                ps.addBatch();
            }

            ps.executeBatch();

        } catch (SQLException e) {
            System.err.println("Error guardando estadísticas: " + e.getMessage());
        }
    }

    private void logQueryExecution(String query, String userEmail, long executionTime,
                                   int rowsAffected, String status, String errorMessage) {
        String sql = "INSERT INTO query_executions " +
                "(query_text, user_email, execution_time_ms, rows_affected, status, error_message) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = database.getConection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, query.length() > 5000 ? query.substring(0, 5000) : query);
            ps.setString(2, userEmail);
            ps.setLong(3, executionTime);
            ps.setInt(4, rowsAffected);
            ps.setString(5, status);
            ps.setString(6, errorMessage);

            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Error registrando ejecución de consulta: " + e.getMessage());
        }
    }
}