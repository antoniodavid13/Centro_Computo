package com.antovdv.centro_computo.controller;

import com.antovdv.centro_computo.model.UserSession;
import com.antovdv.centro_computo.service.DatabaseManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/database")
@CrossOrigin(origins = "*")
public class DatabaseController {

    @Autowired
    private DatabaseManagementService databaseService;

    /**
     * Obtener conexiones activas - ADMIN y TECNICO
     */
    @GetMapping("/connections")
    public ResponseEntity<?> getActiveConnections(HttpSession session) {
        UserSession userSession = (UserSession) session.getAttribute("userSession");

        if (userSession == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!userSession.isAdmin() && !userSession.isTechnician()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<Map<String, Object>> connections = databaseService.getActiveConnections();
        return ResponseEntity.ok(connections);
    }

    /**
     * Obtener estadísticas de rendimiento - ADMIN y TECNICO
     */
    @GetMapping("/statistics")
    public ResponseEntity<?> getStatistics(HttpSession session) {
        UserSession userSession = (UserSession) session.getAttribute("userSession");

        if (userSession == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!userSession.isAdmin() && !userSession.isTechnician()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Map<String, Object> stats = databaseService.getDatabaseStatistics();
        return ResponseEntity.ok(stats);
    }

    /**
     * Ejecutar consulta SQL - SOLO ADMIN
     */
    @PostMapping("/execute-query")
    public ResponseEntity<?> executeQuery(
            @RequestBody Map<String, Object> request,
            HttpSession session) {

        UserSession userSession = (UserSession) session.getAttribute("userSession");

        if (userSession == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!userSession.isAdmin()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Solo los administradores pueden ejecutar consultas SQL");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }

        String query = (String) request.get("query");
        Integer timeout = request.containsKey("timeout") ?
                (Integer) request.get("timeout") : 30;

        if (query == null || query.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "La consulta no puede estar vacía");
            return ResponseEntity.badRequest().body(error);
        }

        Map<String, Object> result = databaseService.executeQueryWithTimeout(
                query, userSession.getEmail(), timeout
        );

        return ResponseEntity.ok(result);
    }

    /**
     * Respaldar estructura de base de datos - SOLO ADMIN
     */
    @PostMapping("/backup-structure")
    public ResponseEntity<?> backupStructure(
            @RequestBody Map<String, String> request,
            HttpSession session) {

        UserSession userSession = (UserSession) session.getAttribute("userSession");

        if (userSession == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!userSession.isAdmin()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Solo los administradores pueden crear respaldos de estructura");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }

        String databaseName = request.get("databaseName");
        String backupName = request.get("backupName");

        if (databaseName == null || backupName == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Faltan parámetros requeridos");
            return ResponseEntity.badRequest().body(error);
        }

        Map<String, Object> result = databaseService.backupDatabaseStructure(
                databaseName, backupName, userSession.getEmail()
        );

        return ResponseEntity.ok(result);
    }
}