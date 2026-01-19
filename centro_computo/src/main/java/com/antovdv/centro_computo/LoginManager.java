package com.antovdv.centro_computo;

import com.antovdv.centro_computo.model.UserSession;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class LoginManager {

    public boolean usuarioExiste(String email) {
        String sql = "SELECT 1 FROM usuarios WHERE email = ?";
        try (Connection conn = database.getConection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("Error al verificar existencia de usuario: " + e.getMessage());
        }
        return false;
    }

    public boolean registrarUsuario(String email, String password, String name, String tipo) {
        String sql = "INSERT INTO usuarios (email, password, name, user_type) VALUES (?, ?, ?, ?)";
        try (Connection conn = database.getConection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);
            ps.setString(2, password);
            ps.setString(3, name);
            ps.setString(4, tipo);

            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            System.err.println("Error al registrar usuario: " + e.getMessage());
            return false;
        }
    }

    public boolean verificarCredenciales(String email, String password) {
        String sql = "SELECT password FROM usuarios WHERE email = ?";
        try (Connection conn = database.getConection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String storedPasswordHash = rs.getString("password");
                    return password.equals(storedPasswordHash);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error al verificar credenciales: " + e.getMessage());
        }
        return false;
    }

    /**
     * Obtiene la información completa del usuario para la sesión
     */
    public UserSession getUserSession(String email) {
        String sql = "SELECT name, user_type FROM usuarios WHERE email = ?";
        try (Connection conn = database.getConection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString("name");
                    String userType = rs.getString("user_type");
                    return new UserSession(email, name, userType);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener sesión de usuario: " + e.getMessage());
        }
        return null;
    }

    public boolean hayUsuariosRegistrados() {
        return true;
    }
}