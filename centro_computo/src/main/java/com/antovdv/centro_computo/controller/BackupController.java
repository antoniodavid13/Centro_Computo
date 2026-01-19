package com.antovdv.centro_computo.controller;

import com.antovdv.centro_computo.model.UserSession;
import com.antovdv.centro_computo.service.BackupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/backups")
@CrossOrigin(origins = "*")
public class BackupController {

    @Autowired
    private BackupService backupService;

    /**
     * Crear un nuevo backup - SOLO ADMIN
     */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createBackup(
            @RequestBody Map<String, String> request,
            HttpSession session) {

        UserSession userSession = (UserSession) session.getAttribute("userSession");

        if (userSession == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "No autenticado");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        if (!userSession.isAdmin()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Solo los administradores pueden crear backups");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }

        String sourcePath = request.get("sourcePath");
        String backupName = request.get("backupName");

        if (sourcePath == null || backupName == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Faltan parámetros requeridos");
            return ResponseEntity.badRequest().body(error);
        }

        Map<String, Object> result = backupService.createBackup(sourcePath, backupName, userSession.getEmail());
        return ResponseEntity.ok(result);
    }

    /**
     * Listar todos los backups - TODOS
     */
    @GetMapping("/list")
    public ResponseEntity<List<Map<String, Object>>> listBackups(HttpSession session) {
        UserSession userSession = (UserSession) session.getAttribute("userSession");

        if (userSession == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<Map<String, Object>> backups = backupService.listBackups();
        return ResponseEntity.ok(backups);
    }

    /**
     * Verificar integridad de un backup - ADMIN y TECNICO
     */
    @GetMapping("/verify/{backupName}")
    public ResponseEntity<Map<String, Object>> verifyBackup(
            @PathVariable String backupName,
            HttpSession session) {

        UserSession userSession = (UserSession) session.getAttribute("userSession");

        if (userSession == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!userSession.isAdmin() && !userSession.isTechnician()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Permisos insuficientes");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }

        Map<String, Object> result = backupService.verifyBackup(backupName);
        return ResponseEntity.ok(result);
    }

    /**
     * Restaurar un backup - SOLO ADMIN
     */
    @PostMapping("/restore")
    public ResponseEntity<Map<String, Object>> restoreBackup(
            @RequestBody Map<String, String> request,
            HttpSession session) {

        UserSession userSession = (UserSession) session.getAttribute("userSession");

        if (userSession == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!userSession.isAdmin()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Solo los administradores pueden restaurar backups");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }

        String backupName = request.get("backupName");
        String targetPath = request.get("targetPath");

        Map<String, Object> result = backupService.restoreBackup(backupName, targetPath);
        return ResponseEntity.ok(result);
    }

    /**
     * Eliminar un backup - SOLO ADMIN
     */
    @DeleteMapping("/{backupName}")
    public ResponseEntity<Map<String, Object>> deleteBackup(
            @PathVariable String backupName,
            HttpSession session) {

        UserSession userSession = (UserSession) session.getAttribute("userSession");

        if (userSession == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!userSession.isAdmin()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Solo los administradores pueden eliminar backups");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }

        Map<String, Object> result = backupService.deleteBackup(backupName);
        return ResponseEntity.ok(result);
    }

    /**
     * Obtener historial de backups - ADMIN y TECNICO
     */
    @GetMapping("/history")
    public ResponseEntity<List<Map<String, Object>>> getHistory(HttpSession session) {
        UserSession userSession = (UserSession) session.getAttribute("userSession");

        if (userSession == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!userSession.isAdmin() && !userSession.isTechnician()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<Map<String, Object>> history = backupService.getBackupHistory();
        return ResponseEntity.ok(history);
    }
}