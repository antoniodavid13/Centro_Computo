# 💻 Centro de Cómputo - Gestión Integral (Beta)

Esta es una aplicación web robusta diseñada para la administración técnica de un centro de cómputo. Actualmente se encuentra en **fase Beta**, ofreciendo herramientas avanzadas que van desde el control de inventario hasta la monitorización de hardware y ejecución de sentencias SQL.

---

## 🚀 Funcionalidades Destacadas

### 🔒 Seguridad y Control de Acceso
* **Validación de Usuarios:** Sistema de login seguro.
* **Roles Dinámicos:** Las funcionalidades (CRUD, Backups, SQL) se habilitan o limitan según el rol asignado al usuario (Admin, Técnico, etc.).

### 📊 Monitorización con OSHI Core
* **Información del Host:** Obtiene datos detallados de tu propio hardware (procesador, memoria, sensores, marca de placa base) gracias a la integración con la librería **OSHI**.

### 🛠️ Administración de Datos y Sistema
* **CRUD Completo:** Gestión total de los activos del centro de cómputo.
* **Consola SQL Integrada:** Permite visualizar tablas y ejecutar sentencias SQL directamente desde la interfaz web, con persistencia inmediata en la base de datos.
* **Gestión de Backups:** Herramienta para realizar copias de seguridad de cualquier archivo crítico del sistema.

### 🎫 Soporte Técnico
* **Sistema de Tickets:** Módulo integrado para la creación y almacenamiento de tickets de soporte para resolución de incidencias.

### 📱 Diseño Moderno
* **Interfaz Responsive:** Diseño adaptativo que permite el uso de la plataforma en dispositivos móviles y de escritorio.

---

## 🛠️ Stack Tecnológico

| Componente | Tecnología |
| :--- | :--- |
| **Lenguaje** | Java 24 (OpenJDK) |
| **Framework** | Spring Boot 4.0.0 |
| **Motor de Plantillas** | Thymeleaf |
| **Persistencia** | Spring Data JPA / Hibernate |
| **Base de Datos** | MySQL 9.1 / H2 Database |
| **Monitorización** | OSHI Core |
| **Frontend** | HTML5, CSS3, JavaScript |

---

## 📋 Requisitos Previos

* **JDK 24** (Configurado en el Path).
* **MySQL Server** (Base de datos llamada `centro_computo`).
* **Maven** para la gestión de dependencias.

---

## ⚙️ Configuración Rápida

1. **Clonar el proyecto:**
   ```bash
   git clone [https://github.com/tu-usuario/centro-computo.git](https://github.com/tu-usuario/centro-computo.git)
