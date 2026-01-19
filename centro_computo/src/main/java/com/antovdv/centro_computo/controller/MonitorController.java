package com.antovdv.centro_computo.controller;

import com.antovdv.centro_computo.dto.SystemMetricsDTO;
import com.antovdv.centro_computo.service.AlertService;
import com.antovdv.centro_computo.service.SystemMonitorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/monitor")
@CrossOrigin(origins = "*")
public class MonitorController {

    @Autowired
    private SystemMonitorService monitorService;

    @Autowired
    private AlertService alertService;

    @GetMapping("/metrics")
    public ResponseEntity<SystemMetricsDTO> getSystemMetrics() {
        SystemMetricsDTO metrics = monitorService.getSystemMetrics();
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/processes")
    public ResponseEntity<List<Map<String, Object>>> getTopProcesses(
            @RequestParam(defaultValue = "100") int limit) {
        List<Map<String, Object>> processes = monitorService.getTopProcesses(limit);
        return ResponseEntity.ok(processes);
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getSystemInfo() {
        Map<String, Object> info = monitorService.getSystemInfo();
        return ResponseEntity.ok(info);
    }

    @GetMapping("/alerts")
    public ResponseEntity<List<Map<String, Object>>> checkAlerts() {
        SystemMetricsDTO metrics = monitorService.getSystemMetrics();
        List<Map<String, Object>> alerts = alertService.checkThresholds(metrics);
        return ResponseEntity.ok(alerts);
    }

    @GetMapping("/alerts/history")
    public ResponseEntity<List<Map<String, Object>>> getAlertHistory() {
        return ResponseEntity.ok(alertService.getAlertHistory());
    }

    @DeleteMapping("/alerts/history")
    public ResponseEntity<Map<String, String>> clearAlertHistory() {
        alertService.clearAlertHistory();
        Map<String, String> response = new HashMap<>();
        response.put("message", "Historial de alertas limpiado");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/alerts/thresholds")
    public ResponseEntity<Map<String, Double>> getThresholds() {
        return ResponseEntity.ok(alertService.getThresholds());
    }

    @PutMapping("/alerts/thresholds")
    public ResponseEntity<Map<String, String>> updateThresholds(
            @RequestBody Map<String, Double> thresholds) {
        if (thresholds.containsKey("cpu")) {
            alertService.setCpuThreshold(thresholds.get("cpu"));
        }
        if (thresholds.containsKey("memory")) {
            alertService.setMemoryThreshold(thresholds.get("memory"));
        }
        if (thresholds.containsKey("disk")) {
            alertService.setDiskThreshold(thresholds.get("disk"));
        }

        Map<String, String> response = new HashMap<>();
        response.put("message", "Umbrales actualizados correctamente");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardData() {
        Map<String, Object> dashboard = new HashMap<>();

        SystemMetricsDTO metrics = monitorService.getSystemMetrics();
        List<Map<String, Object>> processes = monitorService.getTopProcesses(100);
        List<Map<String, Object>> alerts = alertService.checkThresholds(metrics);
        Map<String, Object> systemInfo = monitorService.getSystemInfo();

        dashboard.put("metrics", metrics);
        dashboard.put("topProcesses", processes);
        dashboard.put("alerts", alerts);
        dashboard.put("systemInfo", systemInfo);

        return ResponseEntity.ok(dashboard);
    }
}