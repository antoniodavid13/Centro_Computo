package com.antovdv.centro_computo.controller;

import com.antovdv.centro_computo.service.ProcessManagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/processes")
@CrossOrigin(origins = "*")
public class ProcessController {

    @Autowired
    private ProcessManagerService processManagerService;

    /**
     * Ejecutar un comando externo
     */
    @PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> executeCommand(@RequestBody Map<String, String> request) {
        String command = request.get("command");
        String user = request.getOrDefault("user", "anonymous");

        if (command == null || command.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Comando no puede estar vacío");
            return ResponseEntity.badRequest().body(error);
        }

        Map<String, Object> result = processManagerService.executeCommand(command, user);
        return ResponseEntity.ok(result);
    }

    /**
     * Obtener detalles de un proceso específico
     */
    @GetMapping("/{pid}")
    public ResponseEntity<Map<String, Object>> getProcessDetails(@PathVariable int pid) {
        Map<String, Object> details = processManagerService.getProcessDetails(pid);

        if (details == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Proceso no encontrado");
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(details);
    }

    /**
     * Terminar un proceso
     */
    @DeleteMapping("/{pid}")
    public ResponseEntity<Map<String, Object>> killProcess(
            @PathVariable int pid,
            @RequestParam(defaultValue = "anonymous") String user) {
        Map<String, Object> result = processManagerService.killProcess(pid, user);
        return ResponseEntity.ok(result);
    }

    /**
     * Buscar procesos por nombre
     */
    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> searchProcesses(
            @RequestParam String query) {
        List<Map<String, Object>> results = processManagerService.searchProcesses(query);
        return ResponseEntity.ok(results);
    }

    /**
     * Obtener log de comandos ejecutados
     */
    @GetMapping("/logs")
    public ResponseEntity<List<Map<String, Object>>> getCommandLog(
            @RequestParam(defaultValue = "50") int limit) {
        List<Map<String, Object>> logs = processManagerService.getCommandLog(limit);
        return ResponseEntity.ok(logs);
    }

    /**
     * Limpiar log de comandos
     */
    @DeleteMapping("/logs")
    public ResponseEntity<Map<String, String>> clearCommandLog() {
        processManagerService.clearCommandLog();
        Map<String, String> response = new HashMap<>();
        response.put("message", "Log de comandos limpiado");
        return ResponseEntity.ok(response);
    }
}