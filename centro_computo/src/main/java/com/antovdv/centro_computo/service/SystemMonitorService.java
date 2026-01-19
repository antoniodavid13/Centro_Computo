package com.antovdv.centro_computo.service;

import com.antovdv.centro_computo.dto.SystemMetricsDTO;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.*;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SystemMonitorService {

    private final SystemInfo systemInfo;
    private final HardwareAbstractionLayer hardware;
    private final OperatingSystem os;

    public SystemMonitorService() {
        this.systemInfo = new SystemInfo();
        this.hardware = systemInfo.getHardware();
        this.os = systemInfo.getOperatingSystem();
    }

    public SystemMetricsDTO getSystemMetrics() {
        SystemMetricsDTO metrics = new SystemMetricsDTO();

        // CPU
        CentralProcessor processor = hardware.getProcessor();
        long[] prevTicks = processor.getSystemCpuLoadTicks();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long[] ticks = processor.getSystemCpuLoadTicks();
        double cpuLoad = processor.getSystemCpuLoadBetweenTicks(prevTicks) * 100;

        metrics.setCpuUsage(Math.round(cpuLoad * 100.0) / 100.0);
        metrics.setCpuCores(processor.getLogicalProcessorCount());
        metrics.setCpuModel(processor.getProcessorIdentifier().getName());

        // Memoria
        GlobalMemory memory = hardware.getMemory();
        long totalMemory = memory.getTotal();
        long availableMemory = memory.getAvailable();
        long usedMemory = totalMemory - availableMemory;

        metrics.setTotalMemory(totalMemory / (1024 * 1024)); // MB
        metrics.setUsedMemory(usedMemory / (1024 * 1024)); // MB
        metrics.setAvailableMemory(availableMemory / (1024 * 1024)); // MB
        metrics.setMemoryUsagePercent(Math.round(((double) usedMemory / totalMemory) * 100 * 100.0) / 100.0);

        // Disco
        List<Map<String, Object>> diskInfo = new ArrayList<>();
        for (HWDiskStore disk : hardware.getDiskStores()) {
            Map<String, Object> diskData = new HashMap<>();
            diskData.put("name", disk.getName());
            diskData.put("model", disk.getModel());
            diskData.put("size", disk.getSize() / (1024 * 1024 * 1024)); // GB
            diskData.put("reads", disk.getReads());
            diskData.put("writes", disk.getWrites());
            diskInfo.add(diskData);
        }
        metrics.setDisks(diskInfo);

        // Red
        List<Map<String, Object>> networkInfo = new ArrayList<>();
        for (NetworkIF net : hardware.getNetworkIFs()) {
            if (net.getBytesRecv() > 0 || net.getBytesSent() > 0) {
                Map<String, Object> netData = new HashMap<>();
                netData.put("name", net.getName());
                netData.put("displayName", net.getDisplayName());
                netData.put("ipv4", Arrays.toString(net.getIPv4addr()));
                netData.put("bytesReceived", net.getBytesRecv() / (1024 * 1024)); // MB
                netData.put("bytesSent", net.getBytesSent() / (1024 * 1024)); // MB
                netData.put("speed", net.getSpeed() / (1000 * 1000)); // Mbps
                networkInfo.add(netData);
            }
        }
        metrics.setNetworkInterfaces(networkInfo);

        return metrics;
    }

    public List<Map<String, Object>> getTopProcesses(int limit) {
        List<OSProcess> processes = os.getProcesses();

        return processes.stream()
                .sorted(Comparator.comparingDouble(OSProcess::getProcessCpuLoadCumulative).reversed())
                .limit(limit)
                .map(process -> {
                    Map<String, Object> processData = new HashMap<>();
                    processData.put("pid", process.getProcessID());
                    processData.put("name", process.getName());
                    processData.put("cpuUsage", Math.round(process.getProcessCpuLoadCumulative() * 100 * 100.0) / 100.0);
                    processData.put("memoryUsage", process.getResidentSetSize() / (1024 * 1024)); // MB
                    processData.put("status", process.getState().name());
                    return processData;
                })
                .collect(Collectors.toList());
    }

    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();

        info.put("osName", os.getFamily());
        info.put("osVersion", os.getVersionInfo().toString());
        info.put("manufacturer", os.getManufacturer());
        info.put("uptime", os.getSystemUptime() / 3600); // horas
        info.put("processCount", os.getProcessCount());
        info.put("threadCount", os.getThreadCount());

        return info;
    }
}