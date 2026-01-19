package com.antovdv.centro_computo.service;

import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProcessManagerService {

    private final SystemInfo systemInfo;
    private final OperatingSystem os;
    private final List<Map<String, Object>> commandLog;

    public ProcessManagerService() {
        this.systemInfo = new SystemInfo();
        this.os = systemInfo.getOperatingSystem();
        this.commandLog = new ArrayList<>();
    }

    /**
     * Ejecuta un comando externo
     */
    public Map<String, Object> executeCommand(String command, String user) {
        Map<String, Object> result = new HashMap<>();
        StringBuilder output = new StringBuilder();
        StringBuilder error = new StringBuilder();

        try {
            // Separar el comando en segmentos (como en el código de referencia)
            String[] segmentos = command.split(" ");

            // Crear el proceso con los segmentos
            ProcessBuilder pb = new ProcessBuilder(segmentos);

            // Redirigir errores al stream de salida (como en el código de referencia)
            pb.redirectErrorStream(true);

            // Iniciar el proceso
            Process proceso = pb.start();

            // Tiempo inicial
            long tiempoInicial = System.currentTimeMillis();

            // Leer la salida del proceso
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(proceso.getInputStream()))) {

                // Crear hilo lector (como en el código de referencia)
                Thread leer = new Thread(() -> {
                    String linea;
                    try {
                        while ((linea = br.readLine()) != null) {
                            output.append(linea).append("\n");
                        }
                    } catch (IOException e) {
                        error.append("Error leyendo salida: ").append(e.getMessage());
                    }
                });

                leer.start();

                // Esperar a que el proceso termine con timeout de 30 segundos
                boolean finished = proceso.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);

                if (!finished) {
                    proceso.destroy();
                    result.put("success", false);
                    result.put("error", "Comando excedió el tiempo límite (30 segundos)");
                    result.put("command", command);
                    result.put("timestamp", LocalDateTime.now().toString());
                    logCommand(command, user, false, "Timeout");
                    return result;
                }

                // Esperar a que el hilo lector termine
                leer.join(2000);

                int exitCode = proceso.exitValue();
                long tiempoFinal = System.currentTimeMillis();
                double duracion = (tiempoFinal - tiempoInicial) / 1000.0;

                String outputStr = output.toString();

                result.put("success", exitCode == 0);
                result.put("exitCode", exitCode);
                result.put("output", outputStr.isEmpty() ? "Comando ejecutado sin salida" : outputStr);
                result.put("error", error.toString());
                result.put("command", command);
                result.put("duration", duracion + " segundos");
                result.put("timestamp", LocalDateTime.now().toString());

                // Registrar en el log
                logCommand(command, user, exitCode == 0, outputStr);

            } catch (IOException e) {
                result.put("success", false);
                result.put("error", "Error de I/O: " + e.getMessage());
                result.put("command", command);
                result.put("timestamp", LocalDateTime.now().toString());
                logCommand(command, user, false, "IOException: " + e.getMessage());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                result.put("success", false);
                result.put("error", "Proceso interrumpido: " + e.getMessage());
                result.put("command", command);
                result.put("timestamp", LocalDateTime.now().toString());
                logCommand(command, user, false, "Interrumpido: " + e.getMessage());
            }

        } catch (IOException e) {
            result.put("success", false);
            result.put("error", "Error al crear proceso: " + e.getMessage());
            result.put("command", command);
            result.put("timestamp", LocalDateTime.now().toString());
            logCommand(command, user, false, "Error creación: " + e.getMessage());

        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "Error inesperado: " + e.getMessage());
            result.put("command", command);
            result.put("timestamp", LocalDateTime.now().toString());
            logCommand(command, user, false, "Error: " + e.getMessage());
        }

        return result;
    }

    /**
     * Obtiene información detallada de un proceso por PID
     */
    public Map<String, Object> getProcessDetails(int pid) {
        OSProcess process = os.getProcess(pid);

        if (process == null) {
            return null;
        }

        Map<String, Object> details = new HashMap<>();
        details.put("pid", process.getProcessID());
        details.put("name", process.getName());
        details.put("path", process.getPath());
        details.put("commandLine", process.getCommandLine());
        details.put("user", process.getUser());
        details.put("state", process.getState().name());
        details.put("cpuUsage", Math.round(process.getProcessCpuLoadCumulative() * 100 * 100.0) / 100.0);
        details.put("memoryUsage", process.getResidentSetSize() / (1024 * 1024)); // MB
        details.put("virtualMemory", process.getVirtualSize() / (1024 * 1024)); // MB
        details.put("threadCount", process.getThreadCount());
        details.put("startTime", new Date(process.getStartTime()).toString());
        details.put("upTime", process.getUpTime() / 1000); // segundos

        return details;
    }

    /**
     * Termina un proceso por PID
     */
    public Map<String, Object> killProcess(int pid, String user) {
        Map<String, Object> result = new HashMap<>();

        try {
            OSProcess process = os.getProcess(pid);
            if (process == null) {
                result.put("success", false);
                result.put("message", "Proceso no encontrado");
                return result;
            }

            String processName = process.getName();

            // Intentar terminar el proceso
            boolean killed = process.getProcessID() > 0 && killProcessByPid(pid);

            result.put("success", killed);
            result.put("message", killed ?
                    "Proceso terminado correctamente" :
                    "No se pudo terminar el proceso");
            result.put("pid", pid);
            result.put("processName", processName);

            // Registrar en el log
            logCommand("KILL_PROCESS " + pid + " (" + processName + ")",
                    user, killed,
                    killed ? "Proceso terminado" : "Error al terminar proceso");

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Error: " + e.getMessage());
            logCommand("KILL_PROCESS " + pid, user, false, e.getMessage());
        }

        return result;
    }

    /**
     * Mata un proceso usando comandos del sistema
     */
    private boolean killProcessByPid(int pid) {
        try {
            String osName = System.getProperty("os.name").toLowerCase();
            ProcessBuilder processBuilder;

            if (osName.contains("win")) {
                processBuilder = new ProcessBuilder("taskkill", "/F", "/PID", String.valueOf(pid));
            } else {
                processBuilder = new ProcessBuilder("kill", "-9", String.valueOf(pid));
            }

            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    /**
     * Registra un comando en el log
     */
    private void logCommand(String command, String user, boolean success, String output) {
        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("command", command);
        logEntry.put("user", user != null ? user : "system");
        logEntry.put("success", success);
        logEntry.put("output", output);
        logEntry.put("timestamp", LocalDateTime.now().toString());

        commandLog.add(logEntry);

        // Mantener solo los últimos 100 registros
        if (commandLog.size() > 100) {
            commandLog.remove(0);
        }
    }

    /**
     * Obtiene el log de comandos ejecutados
     */
    public List<Map<String, Object>> getCommandLog(int limit) {
        int size = commandLog.size();
        int fromIndex = Math.max(0, size - limit);

        List<Map<String, Object>> recentLogs = new ArrayList<>(
                commandLog.subList(fromIndex, size)
        );

        // Invertir para mostrar los más recientes primero
        Collections.reverse(recentLogs);

        return recentLogs;
    }

    /**
     * Limpia el log de comandos
     */
    public void clearCommandLog() {
        commandLog.clear();
    }

    /**
     * Obtiene procesos filtrados por nombre
     */
    public List<Map<String, Object>> searchProcesses(String searchTerm) {
        return os.getProcesses().stream()
                .filter(p -> p.getName().toLowerCase().contains(searchTerm.toLowerCase()))
                .limit(50)
                .map(process -> {
                    Map<String, Object> processData = new HashMap<>();
                    processData.put("pid", process.getProcessID());
                    processData.put("name", process.getName());
                    processData.put("cpuUsage", Math.round(process.getProcessCpuLoadCumulative() * 100 * 100.0) / 100.0);
                    processData.put("memoryUsage", process.getResidentSetSize() / (1024 * 1024));
                    processData.put("status", process.getState().name());
                    return processData;
                })
                .collect(Collectors.toList());
    }
}