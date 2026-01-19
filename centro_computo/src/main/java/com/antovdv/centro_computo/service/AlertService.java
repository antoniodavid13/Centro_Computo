package com.antovdv.centro_computo.service;

import com.antovdv.centro_computo.dto.SystemMetricsDTO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class  AlertService {

    // Umbrales configurables
    private double cpuThreshold = 80.0;
    private double memoryThreshold = 85.0;
    private double diskThreshold = 90.0;

    private List<Map<String, Object>> alertHistory = new ArrayList<>();

    public List<Map<String, Object>> checkThresholds(SystemMetricsDTO metrics) {
        List<Map<String, Object>> alerts = new ArrayList<>();

        // Verificar CPU
        if (metrics.getCpuUsage() > cpuThreshold) {
            Map<String, Object> alert = createAlert(
                    "CPU",
                    "HIGH",
                    "Uso de CPU alto: " + metrics.getCpuUsage() + "%",
                    metrics.getCpuUsage()
            );
            alerts.add(alert);
            alertHistory.add(alert);
        }

        // Verificar Memoria
        if (metrics.getMemoryUsagePercent() > memoryThreshold) {
            Map<String, Object> alert = createAlert(
                    "MEMORY",
                    "HIGH",
                    "Uso de memoria alto: " + metrics.getMemoryUsagePercent() + "%",
                    metrics.getMemoryUsagePercent()
            );
            alerts.add(alert);
            alertHistory.add(alert);
        }

        // Limitar historial a últimas 100 alertas
        if (alertHistory.size() > 100) {
            alertHistory = alertHistory.subList(alertHistory.size() - 100, alertHistory.size());
        }

        return alerts;
    }

    private Map<String, Object> createAlert(String type, String severity, String message, Double value) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("type", type);
        alert.put("severity", severity);
        alert.put("message", message);
        alert.put("value", value);
        alert.put("timestamp", LocalDateTime.now().toString());
        return alert;
    }

    public List<Map<String, Object>> getAlertHistory() {
        return new ArrayList<>(alertHistory);
    }

    public void clearAlertHistory() {
        alertHistory.clear();
    }

    // Getters y Setters para umbrales
    public double getCpuThreshold() {
        return cpuThreshold;
    }

    public void setCpuThreshold(double cpuThreshold) {
        this.cpuThreshold = cpuThreshold;
    }

    public double getMemoryThreshold() {
        return memoryThreshold;
    }

    public void setMemoryThreshold(double memoryThreshold) {
        this.memoryThreshold = memoryThreshold;
    }

    public double getDiskThreshold() {
        return diskThreshold;
    }

    public void setDiskThreshold(double diskThreshold) {
        this.diskThreshold = diskThreshold;
    }

    public Map<String, Double> getThresholds() {
        Map<String, Double> thresholds = new HashMap<>();
        thresholds.put("cpu", cpuThreshold);
        thresholds.put("memory", memoryThreshold);
        thresholds.put("disk", diskThreshold);
        return thresholds;
    }
}

