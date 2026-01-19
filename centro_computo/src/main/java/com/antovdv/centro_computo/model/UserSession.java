package com.antovdv.centro_computo.model;

public class UserSession {
    private String email;
    private String name;
    private String userType;

    public UserSession(String email, String name, String userType) {
        this.email = email;
        this.name = name;
        this.userType = userType;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    // Métodos de verificación de permisos
    public boolean isAdmin() {
        return "ADMINISTRADOR".equalsIgnoreCase(userType);
    }

    public boolean isTechnician() {
        return "TECNICO".equalsIgnoreCase(userType);
    }

    public boolean isClient() {
        return "CLIENTE".equalsIgnoreCase(userType);
    }

    public boolean canAccessProcessManagement() {
        return isAdmin() || isTechnician() || isClient(); // Ahora CLIENTES también pueden acceder
    }

    public boolean canExecuteCommands() {
        return isAdmin() || isTechnician(); // Solo ADMIN y TECNICO pueden ejecutar
    }

    public boolean canSearchProcesses() {
        return isAdmin(); // Solo ADMIN puede buscar
    }

    public boolean canKillProcesses() {
        return isAdmin(); // Solo ADMIN puede terminar
    }
}