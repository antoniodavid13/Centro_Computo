package com.antovdv.centro_computo.dto;

import java.util.List;
import java.util.Map;

public class SystemMetricsDTO {

    // CPU
    private Double cpuUsage;
    private Integer cpuCores;
    private String cpuModel;

    // Memoria
    private Long totalMemory;
    private Long usedMemory;
    private Long availableMemory;
    private Double memoryUsagePercent;

    // Disco
    private List<Map<String, Object>> disks;

    // Red
    private List<Map<String, Object>> networkInterfaces;

    // Constructores
    public SystemMetricsDTO() {}

    // Getters y Setters
    public Double getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(Double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public Integer getCpuCores() {
        return cpuCores;
    }

    public void setCpuCores(Integer cpuCores) {
        this.cpuCores = cpuCores;
    }

    public String getCpuModel() {
        return cpuModel;
    }

    public void setCpuModel(String cpuModel) {
        this.cpuModel = cpuModel;
    }

    public Long getTotalMemory() {
        return totalMemory;
    }

    public void setTotalMemory(Long totalMemory) {
        this.totalMemory = totalMemory;
    }

    public Long getUsedMemory() {
        return usedMemory;
    }

    public void setUsedMemory(Long usedMemory) {
        this.usedMemory = usedMemory;
    }

    public Long getAvailableMemory() {
        return availableMemory;
    }

    public void setAvailableMemory(Long availableMemory) {
        this.availableMemory = availableMemory;
    }

    public Double getMemoryUsagePercent() {
        return memoryUsagePercent;
    }

    public void setMemoryUsagePercent(Double memoryUsagePercent) {
        this.memoryUsagePercent = memoryUsagePercent;
    }

    public List<Map<String, Object>> getDisks() {
        return disks;
    }

    public void setDisks(List<Map<String, Object>> disks) {
        this.disks = disks;
    }

    public List<Map<String, Object>> getNetworkInterfaces() {
        return networkInterfaces;
    }

    public void setNetworkInterfaces(List<Map<String, Object>> networkInterfaces) {
        this.networkInterfaces = networkInterfaces;
    }
}