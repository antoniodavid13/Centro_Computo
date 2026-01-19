package com.antovdv.centro_computo.service;

import com.antovdv.centro_computo.database;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Date;
import java.util.stream.Collectors;

@Service
public class BackupService {

    private final String backupBasePath;
    private final List<Map<String, Object>> backupHistory;
    private final Map<String, BackupSchedule> schedules;

    public BackupService() {
        // Directorio base para backups
        this.backupBasePath = System.getProperty("user.home") + File.separator + "TecnoSolutions_Backups";
        this.backupHistory = new ArrayList<>();
        this.schedules = new HashMap<>();

        // Crear directorio de backups si no existe
        try {
            Files.createDirectories(Paths.get(backupBasePath));
        } catch (IOException e) {
            System.err.println("Error creando directorio de backups: " + e.getMessage());
        }
    }

    /**
     * Crea un backup de un directorio usando Git
     */
    public Map<String, Object> createBackup(String sourcePath, String backupName, String user) {
        Map<String, Object> result = new HashMap<>();

        try {
            File sourceDir = new File(sourcePath);

            // Validar que el directorio existe
            if (!sourceDir.exists() || !sourceDir.isDirectory()) {
                result.put("success", false);
                result.put("error", "El directorio no existe o no es válido");
                return result;
            }

            // Crear nombre único para el backup
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String backupDirName = backupName.replaceAll("[^a-zA-Z0-9]", "_") + "_" + timestamp;
            String backupPath = backupBasePath + File.separator + backupDirName;

            // Crear directorio del backup
            Files.createDirectories(Paths.get(backupPath));

            // Inicializar repositorio Git
            executeGitCommand(backupPath, "git", "init");

            // Configurar Git
            executeGitCommand(backupPath, "git", "config", "user.name", user);
            executeGitCommand(backupPath, "git", "config", "user.email", user + "@tecnosolutions.com");

            // Crear .gitignore para excluir archivos innecesarios
            createGitignore(backupPath);

            // Copiar archivos al backup (excluyendo carpetas innecesarias)
            copyDirectorySelective(sourceDir.toPath(), Paths.get(backupPath));

            // Agregar archivos a Git
            executeGitCommand(backupPath, "git", "add", ".");

            // Hacer commit
            String commitMessage = "Backup: " + backupName + " - " + LocalDateTime.now();
            executeGitCommand(backupPath, "git", "commit", "-m", commitMessage);

            // Calcular tamaño del backup
            long size = calculateDirectorySize(Paths.get(backupPath));

            result.put("success", true);
            result.put("backupName", backupDirName);
            result.put("backupPath", backupPath);
            result.put("sourcePath", sourcePath);
            result.put("size", size);
            result.put("timestamp", LocalDateTime.now().toString());
            result.put("user", user);

            // Agregar al historial EN MEMORIA (legacy)
            logBackup(backupDirName, sourcePath, backupPath, size, user, true, "Backup completado");

            // Guardar en BASE DE DATOS
            saveBackupToDatabase(backupDirName, sourcePath, backupPath, size, user, "SUCCESS", null);

        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "Error al crear backup: " + e.getMessage());

            // Registrar error en memoria
            logBackup(backupName, sourcePath, "", 0, user, false, e.getMessage());

            // Registrar error en base de datos
            saveBackupToDatabase(backupName, sourcePath, "", 0, user, "ERROR", e.getMessage());
        }

        return result;
    }

    /**
     * Lista todos los backups disponibles
     */
    public List<Map<String, Object>> listBackups() {
        List<Map<String, Object>> backups = new ArrayList<>();

        try {
            File backupDir = new File(backupBasePath);
            File[] dirs = backupDir.listFiles(File::isDirectory);

            if (dirs != null) {
                for (File dir : dirs) {
                    Map<String, Object> backup = new HashMap<>();
                    backup.put("name", dir.getName());
                    backup.put("path", dir.getAbsolutePath());
                    backup.put("size", calculateDirectorySize(dir.toPath()));
                    backup.put("created", new Date(dir.lastModified()).toString());
                    backup.put("isGitRepo", new File(dir, ".git").exists());

                    // Obtener commits si es repositorio Git
                    if ((Boolean) backup.get("isGitRepo")) {
                        backup.put("commits", getGitCommits(dir.getAbsolutePath()));
                    }

                    backups.add(backup);
                }
            }

            // Ordenar por fecha (más reciente primero)
            backups.sort((a, b) -> b.get("created").toString().compareTo(a.get("created").toString()));

        } catch (Exception e) {
            System.err.println("Error listando backups: " + e.getMessage());
        }

        return backups;
    }

    /**
     * Verifica la integridad de un backup usando Git
     */
    public Map<String, Object> verifyBackup(String backupName) {
        Map<String, Object> result = new HashMap<>();
        String backupPath = backupBasePath + File.separator + backupName;

        try {
            File backupDir = new File(backupPath);

            if (!backupDir.exists()) {
                result.put("success", false);
                result.put("error", "Backup no encontrado");
                return result;
            }

            // Verificar integridad con Git
            String gitStatus = executeGitCommand(backupPath, "git", "fsck", "--full");

            boolean isValid = !gitStatus.toLowerCase().contains("error") &&
                    !gitStatus.toLowerCase().contains("corrupt");

            result.put("success", true);
            result.put("isValid", isValid);
            result.put("backupName", backupName);
            result.put("details", gitStatus);
            result.put("timestamp", LocalDateTime.now().toString());

        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "Error verificando backup: " + e.getMessage());
        }

        return result;
    }

    /**
     * Restaura un backup a una ubicación específica
     */
    public Map<String, Object> restoreBackup(String backupName, String targetPath) {
        Map<String, Object> result = new HashMap<>();
        String backupPath = backupBasePath + File.separator + backupName;

        try {
            File backupDir = new File(backupPath);
            File targetDir = new File(targetPath);

            if (!backupDir.exists()) {
                result.put("success", false);
                result.put("error", "Backup no encontrado");
                return result;
            }

            // Crear directorio destino
            Files.createDirectories(targetDir.toPath());

            // Copiar archivos (excluyendo .git)
            copyDirectory(backupDir.toPath(), targetDir.toPath(), ".git");

            result.put("success", true);
            result.put("backupName", backupName);
            result.put("targetPath", targetPath);
            result.put("timestamp", LocalDateTime.now().toString());

        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "Error restaurando backup: " + e.getMessage());
        }

        return result;
    }

    /**
     * Elimina un backup
     */
    public Map<String, Object> deleteBackup(String backupName) {
        Map<String, Object> result = new HashMap<>();
        String backupPath = backupBasePath + File.separator + backupName;

        try {
            Path path = Paths.get(backupPath);

            if (!Files.exists(path)) {
                result.put("success", false);
                result.put("error", "Backup no encontrado");
                return result;
            }

            // Eliminar directorio recursivamente
            deleteDirectory(path);

            result.put("success", true);
            result.put("backupName", backupName);
            result.put("message", "Backup eliminado correctamente");

        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "Error eliminando backup: " + e.getMessage());
        }

        return result;
    }

    /**
     * Obtiene el historial de backups DESDE LA BASE DE DATOS
     */
    public List<Map<String, Object>> getBackupHistory() {
        List<Map<String, Object>> history = new ArrayList<>();

        String sql = "SELECT id, backup_name, source_path, backup_path, size_bytes, " +
                "user_email, status, error_message, created_at " +
                "FROM backups " +
                "ORDER BY created_at DESC " +
                "LIMIT 100";

        try (Connection conn = database.getConection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> backup = new HashMap<>();
                backup.put("id", rs.getInt("id"));
                backup.put("name", rs.getString("backup_name"));
                backup.put("source", rs.getString("source_path"));
                backup.put("path", rs.getString("backup_path"));
                backup.put("size", rs.getLong("size_bytes"));
                backup.put("user", rs.getString("user_email"));
                backup.put("success", "SUCCESS".equals(rs.getString("status")));
                backup.put("message", rs.getString("error_message"));
                backup.put("timestamp", rs.getTimestamp("created_at").toString());
                history.add(backup);
            }

        } catch (SQLException e) {
            System.err.println("Error obteniendo historial de backups: " + e.getMessage());
        }

        return history;
    }

    // Métodos auxiliares

    private String executeGitCommand(String workingDir, String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(workingDir));
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        process.waitFor();
        return output.toString();
    }

    private void copyDirectory(Path source, Path target, String... excludes) throws IOException {
        Set<String> excludeSet = new HashSet<>(Arrays.asList(excludes));

        Files.walk(source).forEach(sourcePath -> {
            try {
                Path targetPath = target.resolve(source.relativize(sourcePath));

                // Saltar archivos/directorios excluidos
                if (excludeSet.stream().anyMatch(ex -> sourcePath.toString().contains(ex))) {
                    return;
                }

                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                System.err.println("Error copiando: " + e.getMessage());
            }
        });
    }

    /**
     * Copia un directorio de forma selectiva, excluyendo carpetas innecesarias
     */
    private void copyDirectorySelective(Path source, Path target) throws IOException {
        // Lista de carpetas y archivos a excluir
        Set<String> excludePatterns = new HashSet<>(Arrays.asList(
                ".git",           // Repositorios Git
                "node_modules",   // Dependencias Node.js
                "target",         // Compilaciones Java/Maven
                "build",          // Compilaciones Gradle
                "dist",           // Distribuciones
                "out",            // Output folders
                ".idea",          // IntelliJ IDEA
                ".vscode",        // Visual Studio Code
                ".eclipse",       // Eclipse
                "__pycache__",    // Python cache
                ".DS_Store",      // macOS
                "Thumbs.db",      // Windows
                "desktop.ini",    // Windows
                "$RECYCLE.BIN",   // Windows Recycle Bin
                "System Volume Information", // Windows
                ".Trash",         // macOS/Linux
                "temp",           // Temporales
                "tmp",            // Temporales
                "cache",          // Cache
                ".cache",         // Cache
                "logs",           // Logs (opcional)
                "*.log",          // Archivos de log
                "*.tmp",          // Archivos temporales
                "*.temp"          // Archivos temporales
        ));

        Files.walk(source)
                .filter(sourcePath -> {
                    // Obtener el path relativo
                    Path relativePath = source.relativize(sourcePath);
                    String pathStr = relativePath.toString();

                    // Verificar si algún componente del path debe ser excluido
                    for (String exclude : excludePatterns) {
                        if (pathStr.contains(exclude) || pathStr.endsWith(exclude)) {
                            return false;
                        }
                    }

                    // Excluir archivos muy grandes (>100MB)
                    try {
                        if (Files.isRegularFile(sourcePath)) {
                            long fileSize = Files.size(sourcePath);
                            if (fileSize > 100 * 1024 * 1024) { // 100 MB
                                System.out.println("Excluyendo archivo grande: " + sourcePath + " (" + (fileSize / (1024 * 1024)) + " MB)");
                                return false;
                            }
                        }
                    } catch (IOException e) {
                        return false;
                    }

                    return true;
                })
                .forEach(sourcePath -> {
                    try {
                        Path targetPath = target.resolve(source.relativize(sourcePath));

                        if (Files.isDirectory(sourcePath)) {
                            Files.createDirectories(targetPath);
                        } else {
                            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        System.err.println("Error copiando: " + sourcePath + " - " + e.getMessage());
                    }
                });
    }

    /**
     * Crea un archivo .gitignore en el backup
     */
    private void createGitignore(String backupPath) {
        try {
            Path gitignorePath = Paths.get(backupPath, ".gitignore");
            List<String> gitignoreContent = Arrays.asList(
                    "# Archivos del sistema",
                    ".DS_Store",
                    "Thumbs.db",
                    "desktop.ini",
                    "",
                    "# IDEs",
                    ".idea/",
                    ".vscode/",
                    ".eclipse/",
                    "*.swp",
                    "*.swo",
                    "",
                    "# Compilaciones",
                    "*.class",
                    "*.o",
                    "*.so",
                    "*.dll",
                    "*.exe",
                    "",
                    "# Logs y temporales",
                    "*.log",
                    "*.tmp",
                    "*.temp",
                    "",
                    "# Dependencias (por si acaso)",
                    "node_modules/",
                    "target/",
                    "build/",
                    "dist/"
            );

            Files.write(gitignorePath, gitignoreContent, StandardOpenOption.CREATE);

        } catch (IOException e) {
            System.err.println("Error creando .gitignore: " + e.getMessage());
        }
    }

    private long calculateDirectorySize(Path path) throws IOException {
        return Files.walk(path)
                .filter(p -> p.toFile().isFile())
                .mapToLong(p -> p.toFile().length())
                .sum();
    }

    private void deleteDirectory(Path path) throws IOException {
        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    private List<String> getGitCommits(String repoPath) {
        try {
            String output = executeGitCommand(repoPath, "git", "log", "--pretty=format:%h - %s (%ci)", "-10");
            return Arrays.asList(output.split("\n"));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void logBackup(String name, String source, String path, long size, String user, boolean success, String message) {
        Map<String, Object> log = new HashMap<>();
        log.put("name", name);
        log.put("source", source);
        log.put("path", path);
        log.put("size", size);
        log.put("user", user);
        log.put("success", success);
        log.put("message", message);
        log.put("timestamp", LocalDateTime.now().toString());

        backupHistory.add(log);

        // Mantener solo los últimos 100 registros
        if (backupHistory.size() > 100) {
            backupHistory.remove(0);
        }
    }

    /**
     * Guarda el backup en la base de datos
     */
    private void saveBackupToDatabase(String name, String source, String path, long size,
                                      String user, String status, String errorMessage) {
        String sql = "INSERT INTO backups (backup_name, source_path, backup_path, size_bytes, user_email, status, error_message) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = database.getConection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, name);
            ps.setString(2, source);
            ps.setString(3, path);
            ps.setLong(4, size);
            ps.setString(5, user);
            ps.setString(6, status);
            ps.setString(7, errorMessage);

            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Error guardando backup en BD: " + e.getMessage());
        }
    }

    // Clase interna para programación de backups
    public static class BackupSchedule {
        private String name;
        private String sourcePath;
        private String frequency; // DAILY, WEEKLY, MONTHLY
        private String lastRun;
        private boolean enabled;

        public BackupSchedule(String name, String sourcePath, String frequency) {
            this.name = name;
            this.sourcePath = sourcePath;
            this.frequency = frequency;
            this.enabled = true;
        }

        // Getters y setters
        public String getName() { return name; }
        public String getSourcePath() { return sourcePath; }
        public String getFrequency() { return frequency; }
        public String getLastRun() { return lastRun; }
        public boolean isEnabled() { return enabled; }
        public void setLastRun(String lastRun) { this.lastRun = lastRun; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}