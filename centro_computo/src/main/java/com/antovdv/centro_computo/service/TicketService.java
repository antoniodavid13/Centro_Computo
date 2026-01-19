package com.antovdv.centro_computo.service;

import com.antovdv.centro_computo.database;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class TicketService {

    /**
     * Genera un número de ticket único
     */
    private String generateTicketNumber() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String sql = "SELECT COUNT(*) + 1 as next_num FROM tickets WHERE DATE(created_at) = CURDATE()";

        try (Connection conn = database.getConection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                int nextNum = rs.getInt("next_num");
                return String.format("TKT-%s-%03d", date, nextNum);
            }
        } catch (SQLException e) {
            System.err.println("Error generando número de ticket: " + e.getMessage());
        }

        return "TKT-" + date + "-" + System.currentTimeMillis() % 1000;
    }

    /**
     * Crear un nuevo ticket
     */
    public Map<String, Object> createTicket(String title, String description, String category,
                                            String priority, String userEmail, String userName) {
        Map<String, Object> result = new HashMap<>();

        String ticketNumber = generateTicketNumber();

        String sql = "INSERT INTO tickets (ticket_number, title, description, category, priority, " +
                "created_by_email, created_by_name) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = database.getConection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, ticketNumber);
            ps.setString(2, title);
            ps.setString(3, description);
            ps.setString(4, category);
            ps.setString(5, priority);
            ps.setString(6, userEmail);
            ps.setString(7, userName);

            int affected = ps.executeUpdate();

            if (affected > 0) {
                ResultSet generatedKeys = ps.getGeneratedKeys();
                if (generatedKeys.next()) {
                    int ticketId = generatedKeys.getInt(1);

                    // Registrar en historial
                    logTicketHistory(ticketId, userEmail, userName, "CREATED", null, "ABIERTO",
                            "Ticket creado");

                    result.put("success", true);
                    result.put("ticketId", ticketId);
                    result.put("ticketNumber", ticketNumber);
                    result.put("message", "Ticket creado exitosamente");
                }
            } else {
                result.put("success", false);
                result.put("error", "No se pudo crear el ticket");
            }

        } catch (SQLException e) {
            result.put("success", false);
            result.put("error", "Error al crear ticket: " + e.getMessage());
        }

        return result;
    }

    /**
     * Obtener todos los tickets con filtros opcionales
     */
    public List<Map<String, Object>> getTickets(String status, String priority, String category,
                                                String assignedTo, String createdBy,
                                                String userEmail, String userType) {
        List<Map<String, Object>> tickets = new ArrayList<>();

        StringBuilder sql = new StringBuilder(
                "SELECT t.*, " +
                        "(SELECT COUNT(*) FROM ticket_comments WHERE ticket_id = t.id) as comment_count " +
                        "FROM tickets t WHERE 1=1 "
        );

        List<Object> params = new ArrayList<>();

        // Si es CLIENTE, solo ve sus propios tickets
        if ("CLIENTE".equalsIgnoreCase(userType)) {
            sql.append("AND t.created_by_email = ? ");
            params.add(userEmail);
        }

        // Filtros opcionales
        if (status != null && !status.isEmpty()) {
            sql.append("AND t.status = ? ");
            params.add(status);
        }
        if (priority != null && !priority.isEmpty()) {
            sql.append("AND t.priority = ? ");
            params.add(priority);
        }
        if (category != null && !category.isEmpty()) {
            sql.append("AND t.category = ? ");
            params.add(category);
        }
        if (assignedTo != null && !assignedTo.isEmpty()) {
            sql.append("AND t.assigned_to_email = ? ");
            params.add(assignedTo);
        }
        if (createdBy != null && !createdBy.isEmpty()) {
            sql.append("AND t.created_by_email = ? ");
            params.add(createdBy);
        }

        sql.append("ORDER BY " +
                "CASE t.priority " +
                "  WHEN 'CRITICA' THEN 1 " +
                "  WHEN 'ALTA' THEN 2 " +
                "  WHEN 'MEDIA' THEN 3 " +
                "  WHEN 'BAJA' THEN 4 " +
                "END, t.created_at DESC");

        try (Connection conn = database.getConection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tickets.add(mapTicketFromResultSet(rs));
                }
            }

        } catch (SQLException e) {
            System.err.println("Error obteniendo tickets: " + e.getMessage());
        }

        return tickets;
    }

    /**
     * Obtener un ticket por ID
     */
    public Map<String, Object> getTicketById(int ticketId) {
        String sql = "SELECT t.*, " +
                "(SELECT COUNT(*) FROM ticket_comments WHERE ticket_id = t.id) as comment_count " +
                "FROM tickets t WHERE t.id = ?";

        try (Connection conn = database.getConection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, ticketId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapTicketFromResultSet(rs);
                }
            }

        } catch (SQLException e) {
            System.err.println("Error obteniendo ticket: " + e.getMessage());
        }

        return null;
    }

    /**
     * Asignar ticket a un técnico
     */
    public Map<String, Object> assignTicket(int ticketId, String technicianEmail,
                                            String technicianName, String assignerEmail,
                                            String assignerName) {
        Map<String, Object> result = new HashMap<>();

        Map<String, Object> ticket = getTicketById(ticketId);
        String oldAssigned = ticket != null ? (String) ticket.get("assignedToEmail") : null;

        String sql = "UPDATE tickets SET assigned_to_email = ?, assigned_to_name = ?, " +
                "status = CASE WHEN status = 'ABIERTO' THEN 'EN_PROGRESO' ELSE status END " +
                "WHERE id = ?";

        try (Connection conn = database.getConection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, technicianEmail);
            ps.setString(2, technicianName);
            ps.setInt(3, ticketId);

            int affected = ps.executeUpdate();

            if (affected > 0) {
                logTicketHistory(ticketId, assignerEmail, assignerName, "ASSIGNED",
                        oldAssigned, technicianEmail,
                        "Ticket asignado a " + technicianName);

                result.put("success", true);
                result.put("message", "Ticket asignado correctamente");
            } else {
                result.put("success", false);
                result.put("error", "Ticket no encontrado");
            }

        } catch (SQLException e) {
            result.put("success", false);
            result.put("error", "Error al asignar ticket: " + e.getMessage());
        }

        return result;
    }

    /**
     * Cambiar estado del ticket
     */
    public Map<String, Object> changeStatus(int ticketId, String newStatus,
                                            String userEmail, String userName) {
        Map<String, Object> result = new HashMap<>();

        Map<String, Object> ticket = getTicketById(ticketId);
        String oldStatus = ticket != null ? (String) ticket.get("status") : null;

        StringBuilder sql = new StringBuilder("UPDATE tickets SET status = ?");

        if ("RESUELTO".equals(newStatus)) {
            sql.append(", resolved_at = NOW()");
        } else if ("CERRADO".equals(newStatus)) {
            sql.append(", closed_at = NOW()");
        }

        sql.append(" WHERE id = ?");

        try (Connection conn = database.getConection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            ps.setString(1, newStatus);
            ps.setInt(2, ticketId);

            int affected = ps.executeUpdate();

            if (affected > 0) {
                logTicketHistory(ticketId, userEmail, userName, "STATUS_CHANGED",
                        oldStatus, newStatus,
                        "Estado cambiado de " + oldStatus + " a " + newStatus);

                result.put("success", true);
                result.put("message", "Estado actualizado correctamente");
            } else {
                result.put("success", false);
                result.put("error", "Ticket no encontrado");
            }

        } catch (SQLException e) {
            result.put("success", false);
            result.put("error", "Error al cambiar estado: " + e.getMessage());
        }

        return result;
    }

    /**
     * Cambiar prioridad del ticket
     */
    public Map<String, Object> changePriority(int ticketId, String newPriority,
                                              String userEmail, String userName) {
        Map<String, Object> result = new HashMap<>();

        Map<String, Object> ticket = getTicketById(ticketId);
        String oldPriority = ticket != null ? (String) ticket.get("priority") : null;

        String sql = "UPDATE tickets SET priority = ? WHERE id = ?";

        try (Connection conn = database.getConection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, newPriority);
            ps.setInt(2, ticketId);

            int affected = ps.executeUpdate();

            if (affected > 0) {
                logTicketHistory(ticketId, userEmail, userName, "PRIORITY_CHANGED",
                        oldPriority, newPriority,
                        "Prioridad cambiada de " + oldPriority + " a " + newPriority);

                result.put("success", true);
                result.put("message", "Prioridad actualizada correctamente");
            } else {
                result.put("success", false);
                result.put("error", "Ticket no encontrado");
            }

        } catch (SQLException e) {
            result.put("success", false);
            result.put("error", "Error al cambiar prioridad: " + e.getMessage());
        }

        return result;
    }

    /**
     * Agregar comentario a un ticket
     */
    public Map<String, Object> addComment(int ticketId, String comment, boolean isInternal,
                                          String userEmail, String userName) {
        Map<String, Object> result = new HashMap<>();

        String sql = "INSERT INTO ticket_comments (ticket_id, user_email, user_name, comment, is_internal) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = database.getConection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, ticketId);
            ps.setString(2, userEmail);
            ps.setString(3, userName);
            ps.setString(4, comment);
            ps.setBoolean(5, isInternal);

            int affected = ps.executeUpdate();

            if (affected > 0) {
                ResultSet generatedKeys = ps.getGeneratedKeys();
                int commentId = 0;
                if (generatedKeys.next()) {
                    commentId = generatedKeys.getInt(1);
                }

                logTicketHistory(ticketId, userEmail, userName, "COMMENT_ADDED",
                        null, null, "Comentario agregado" + (isInternal ? " (interno)" : ""));

                result.put("success", true);
                result.put("commentId", commentId);
                result.put("message", "Comentario agregado correctamente");
            } else {
                result.put("success", false);
                result.put("error", "No se pudo agregar el comentario");
            }

        } catch (SQLException e) {
            result.put("success", false);
            result.put("error", "Error al agregar comentario: " + e.getMessage());
        }

        return result;
    }

    /**
     * Obtener comentarios de un ticket
     */
    public List<Map<String, Object>> getComments(int ticketId, boolean includeInternal) {
        List<Map<String, Object>> comments = new ArrayList<>();

        String sql = "SELECT * FROM ticket_comments WHERE ticket_id = ? " +
                (includeInternal ? "" : "AND is_internal = FALSE ") +
                "ORDER BY created_at ASC";

        try (Connection conn = database.getConection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, ticketId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> comment = new HashMap<>();
                    comment.put("id", rs.getInt("id"));
                    comment.put("ticketId", rs.getInt("ticket_id"));
                    comment.put("userEmail", rs.getString("user_email"));
                    comment.put("userName", rs.getString("user_name"));
                    comment.put("comment", rs.getString("comment"));
                    comment.put("isInternal", rs.getBoolean("is_internal"));
                    comment.put("createdAt", rs.getTimestamp("created_at").toString());
                    comments.add(comment);
                }
            }

        } catch (SQLException e) {
            System.err.println("Error obteniendo comentarios: " + e.getMessage());
        }

        return comments;
    }

    /**
     * Obtener historial de un ticket
     */
    public List<Map<String, Object>> getTicketHistory(int ticketId) {
        List<Map<String, Object>> history = new ArrayList<>();

        String sql = "SELECT * FROM ticket_history WHERE ticket_id = ? ORDER BY created_at DESC";

        try (Connection conn = database.getConection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, ticketId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("id", rs.getInt("id"));
                    entry.put("ticketId", rs.getInt("ticket_id"));
                    entry.put("userEmail", rs.getString("user_email"));
                    entry.put("userName", rs.getString("user_name"));
                    entry.put("action", rs.getString("action"));
                    entry.put("oldValue", rs.getString("old_value"));
                    entry.put("newValue", rs.getString("new_value"));
                    entry.put("description", rs.getString("description"));
                    entry.put("createdAt", rs.getTimestamp("created_at").toString());
                    history.add(entry);
                }
            }

        } catch (SQLException e) {
            System.err.println("Error obteniendo historial: " + e.getMessage());
        }

        return history;
    }

    /**
     * Obtener estadísticas de tickets
     */
    public Map<String, Object> getTicketStatistics() {
        Map<String, Object> stats = new HashMap<>();

        try (Connection conn = database.getConection()) {

            stats.put("total", countTickets(conn, null, null));

            Map<String, Integer> byStatus = new HashMap<>();
            byStatus.put("ABIERTO", countTickets(conn, "status", "ABIERTO"));
            byStatus.put("EN_PROGRESO", countTickets(conn, "status", "EN_PROGRESO"));
            byStatus.put("PENDIENTE", countTickets(conn, "status", "PENDIENTE"));
            byStatus.put("RESUELTO", countTickets(conn, "status", "RESUELTO"));
            byStatus.put("CERRADO", countTickets(conn, "status", "CERRADO"));
            stats.put("byStatus", byStatus);

            Map<String, Integer> byPriority = new HashMap<>();
            byPriority.put("CRITICA", countTickets(conn, "priority", "CRITICA"));
            byPriority.put("ALTA", countTickets(conn, "priority", "ALTA"));
            byPriority.put("MEDIA", countTickets(conn, "priority", "MEDIA"));
            byPriority.put("BAJA", countTickets(conn, "priority", "BAJA"));
            stats.put("byPriority", byPriority);

            String sqlUnassigned = "SELECT COUNT(*) FROM tickets WHERE assigned_to_email IS NULL AND status NOT IN ('RESUELTO', 'CERRADO')";
            try (PreparedStatement ps = conn.prepareStatement(sqlUnassigned);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    stats.put("unassigned", rs.getInt(1));
                }
            }

        } catch (SQLException e) {
            System.err.println("Error obteniendo estadísticas: " + e.getMessage());
        }

        return stats;
    }

    /**
     * Obtener lista de técnicos para asignación
     */
    public List<Map<String, Object>> getTechnicians() {
        List<Map<String, Object>> technicians = new ArrayList<>();

        String sql = "SELECT email, name FROM usuarios WHERE user_type IN ('ADMINISTRADOR', 'TECNICO') ORDER BY name";

        try (Connection conn = database.getConection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> tech = new HashMap<>();
                tech.put("email", rs.getString("email"));
                tech.put("name", rs.getString("name"));
                technicians.add(tech);
            }

        } catch (SQLException e) {
            System.err.println("Error obteniendo técnicos: " + e.getMessage());
        }

        return technicians;
    }

    // ==================== MÉTODOS AUXILIARES ====================

    private Map<String, Object> mapTicketFromResultSet(ResultSet rs) throws SQLException {
        Map<String, Object> ticket = new HashMap<>();
        ticket.put("id", rs.getInt("id"));
        ticket.put("ticketNumber", rs.getString("ticket_number"));
        ticket.put("title", rs.getString("title"));
        ticket.put("description", rs.getString("description"));
        ticket.put("category", rs.getString("category"));
        ticket.put("priority", rs.getString("priority"));
        ticket.put("status", rs.getString("status"));
        ticket.put("createdByEmail", rs.getString("created_by_email"));
        ticket.put("createdByName", rs.getString("created_by_name"));
        ticket.put("assignedToEmail", rs.getString("assigned_to_email"));
        ticket.put("assignedToName", rs.getString("assigned_to_name"));
        ticket.put("createdAt", rs.getTimestamp("created_at").toString());

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        ticket.put("updatedAt", updatedAt != null ? updatedAt.toString() : null);

        Timestamp resolvedAt = rs.getTimestamp("resolved_at");
        ticket.put("resolvedAt", resolvedAt != null ? resolvedAt.toString() : null);

        Timestamp closedAt = rs.getTimestamp("closed_at");
        ticket.put("closedAt", closedAt != null ? closedAt.toString() : null);

        try {
            ticket.put("commentCount", rs.getInt("comment_count"));
        } catch (SQLException e) {
            ticket.put("commentCount", 0);
        }

        return ticket;
    }

    private void logTicketHistory(int ticketId, String userEmail, String userName,
                                  String action, String oldValue, String newValue,
                                  String description) {
        String sql = "INSERT INTO ticket_history (ticket_id, user_email, user_name, action, " +
                "old_value, new_value, description) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = database.getConection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, ticketId);
            ps.setString(2, userEmail);
            ps.setString(3, userName);
            ps.setString(4, action);
            ps.setString(5, oldValue);
            ps.setString(6, newValue);
            ps.setString(7, description);

            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Error registrando historial: " + e.getMessage());
        }
    }

    private int countTickets(Connection conn, String field, String value) throws SQLException {
        String sql = "SELECT COUNT(*) FROM tickets" +
                (field != null ? " WHERE " + field + " = ?" : "");

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (field != null) {
                ps.setString(1, value);
            }

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }
}