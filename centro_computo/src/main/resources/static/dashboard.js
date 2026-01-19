// Dashboard - Sistema de Monitoreo en Tiempo Real

// Configuración
const UPDATE_INTERVAL = 3000; // 3 segundos
const NETWORKS_PER_PAGE = 6; // Interfaces de red por página
let currentNetworkPage = 1;
let allNetworkInterfaces = [];

// Actualizar dashboard completo
function updateDashboard() {
    fetch('/api/monitor/dashboard')
        .then(response => {
            if (!response.ok) {
                throw new Error('Error en la respuesta del servidor');
            }
            return response.json();
        })
        .then(data => {
            updateMetrics(data.metrics);
            updateSystemInfo(data.systemInfo);
            updateProcesses(data.topProcesses);
            updateAlerts(data.alerts);
        })
        .catch(error => {
            console.error('Error al actualizar dashboard:', error);
            showError('No se pudo conectar con el servidor');
        });
}

// Actualizar métricas principales
function updateMetrics(metrics) {
    if (!metrics) return;

    // CPU
    const cpuValue = document.getElementById('cpuValue');
    const cpuDetail = document.getElementById('cpuDetail');
    if (cpuValue && cpuDetail) {
        cpuValue.textContent = metrics.cpuUsage.toFixed(1) + '%';
        cpuDetail.textContent = `${metrics.cpuCores} núcleos`;

        // Cambiar color según uso
        cpuValue.style.color = metrics.cpuUsage > 80 ? '#e74c3c' :
                               metrics.cpuUsage > 60 ? '#f39c12' : '#2ecc71';
    }

    // Memoria
    const memoryValue = document.getElementById('memoryValue');
    const memoryDetail = document.getElementById('memoryDetail');
    if (memoryValue && memoryDetail) {
        memoryValue.textContent = metrics.memoryUsagePercent.toFixed(1) + '%';
        memoryDetail.textContent =
            `${(metrics.usedMemory / 1024).toFixed(1)} GB / ${(metrics.totalMemory / 1024).toFixed(1)} GB`;

        // Cambiar color según uso
        memoryValue.style.color = metrics.memoryUsagePercent > 85 ? '#e74c3c' :
                                  metrics.memoryUsagePercent > 70 ? '#f39c12' : '#2ecc71';
    }

    // Disco
    const diskValue = document.getElementById('diskValue');
    const diskDetail = document.getElementById('diskDetail');
    if (diskValue && diskDetail && metrics.disks && metrics.disks.length > 0) {
        const totalSize = metrics.disks.reduce((sum, disk) => sum + disk.size, 0);
        diskValue.textContent = totalSize.toFixed(0) + ' GB';
        diskDetail.textContent = `${metrics.disks.length} disco(s)`;
    }

    // Red (actualizar sección)
    if (metrics.networkInterfaces) {
        updateNetworkInfo(metrics.networkInterfaces);
    }
}

// Actualizar información del sistema
function updateSystemInfo(info) {
    if (!info) return;

    const container = document.getElementById('systemInfo');
    if (!container) return;

    const uptimeHours = info.uptime || 0;
    const uptimeDays = Math.floor(uptimeHours / 24);
    const remainingHours = uptimeHours % 24;
    const uptimeText = uptimeDays > 0
        ? `${uptimeDays}d ${remainingHours}h`
        : `${uptimeHours}h`;

    container.innerHTML = `
        <div class="info-item">
            <div class="info-label">Sistema Operativo</div>
            <div>${escapeHtml(info.osName || 'N/A')}</div>
        </div>
        <div class="info-item">
            <div class="info-label">Versión</div>
            <div>${escapeHtml(info.osVersion || 'N/A')}</div>
        </div>
        <div class="info-item">
            <div class="info-label">Tiempo Activo</div>
            <div>${uptimeText}</div>
        </div>
        <div class="info-item">
            <div class="info-label">Procesos</div>
            <div>${formatNumber(info.processCount || 0)}</div>
        </div>
        <div class="info-item">
            <div class="info-label">Hilos</div>
            <div>${formatNumber(info.threadCount || 0)}</div>
        </div>
        <div class="info-item">
            <div class="info-label">Fabricante</div>
            <div>${escapeHtml(info.manufacturer || 'N/A')}</div>
        </div>
    `;
}

// Actualizar tabla de procesos - TODOS LOS PROCESOS CON SCROLL
function updateProcesses(processes) {
    if (!processes || processes.length === 0) return;

    const tbody = document.getElementById('processTableBody');
    if (!tbody) return;

    // Mostrar TODOS los procesos
    tbody.innerHTML = processes.map(proc => {
        const cpuClass = proc.cpuUsage > 50 ? 'cpu-high' :
                        proc.cpuUsage > 25 ? 'cpu-medium' : '';

        return `
        <tr>
            <td>${proc.pid}</td>
            <td title="${escapeHtml(proc.name)}">${escapeHtml(truncateText(proc.name, 40))}</td>
            <td class="${cpuClass}">${proc.cpuUsage.toFixed(1)}%</td>
            <td>${proc.memoryUsage.toFixed(1)} MB</td>
            <td>${escapeHtml(proc.status)}</td>
            <td>
                <button class="btn-kill-small" onclick="killProcessFromDashboard(${proc.pid}, '${escapeHtml(proc.name)}')" title="Terminar proceso">
                    ❌
                </button>
            </td>
        </tr>
        `;
    }).join('');
}

// Terminar proceso desde el dashboard
function killProcessFromDashboard(pid, name) {
    if (!confirm(`¿Está seguro de terminar el proceso "${name}" (PID: ${pid})?`)) {
        return;
    }

    fetch(`/api/processes/${pid}?user=admin`, {
        method: 'DELETE'
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            alert(`✓ Proceso "${name}" terminado correctamente`);
            // Actualizar dashboard inmediatamente
            updateDashboard();
        } else {
            alert(`✗ Error: ${data.message}`);
        }
    })
    .catch(error => {
        alert(`✗ Error al terminar proceso: ${error.message}`);
    });
}

// Actualizar información de red con paginación
function updateNetworkInfo(interfaces) {
    if (!interfaces || interfaces.length === 0) return;

    const container = document.getElementById('networkInfo');
    if (!container) return;

    const activeInterfaces = interfaces.filter(net =>
        net.bytesReceived > 0 || net.bytesSent > 0
    );

    const paginationDiv = document.getElementById('networkPagination');

    if (activeInterfaces.length === 0) {
        container.innerHTML = '<div class="loading">No hay interfaces de red activas</div>';
        if (paginationDiv) {
            paginationDiv.style.display = 'none';
        }
        return;
    }

    // Guardar todas las interfaces
    allNetworkInterfaces = activeInterfaces;

    // Calcular paginación
    const totalPages = Math.ceil(allNetworkInterfaces.length / NETWORKS_PER_PAGE);
    const startIndex = (currentNetworkPage - 1) * NETWORKS_PER_PAGE;
    const endIndex = startIndex + NETWORKS_PER_PAGE;
    const currentInterfaces = allNetworkInterfaces.slice(startIndex, endIndex);

    // Renderizar interfaces de la página actual
    container.innerHTML = currentInterfaces.map(net => {
        const displayName = escapeHtml(net.displayName || net.name);
        const shortName = displayName.length > 50 ? displayName.substring(0, 47) + '...' : displayName;

        return `
        <div class="network-card">
            <div class="info-label" title="${displayName}">${shortName}</div>
            <div class="network-card-content">
                <div><strong>IP:</strong> ${escapeHtml(net.ipv4 || 'N/A')}</div>
                <div><strong>Recibido:</strong> ${net.bytesReceived} MB</div>
                <div><strong>Enviado:</strong> ${net.bytesSent} MB</div>
                <div><strong>Velocidad:</strong> ${net.speed} Mbps</div>
            </div>
        </div>
        `;
    }).join('');

    // Mostrar controles de paginación si hay más de una página
    if (paginationDiv) {
        if (totalPages > 1) {
            paginationDiv.style.display = 'flex';
            const pageInfo = document.getElementById('pageInfo');
            const prevBtn = document.getElementById('prevPage');
            const nextBtn = document.getElementById('nextPage');

            if (pageInfo) pageInfo.textContent = `Página ${currentNetworkPage} de ${totalPages}`;
            if (prevBtn) prevBtn.disabled = currentNetworkPage === 1;
            if (nextBtn) nextBtn.disabled = currentNetworkPage === totalPages;
        } else {
            paginationDiv.style.display = 'none';
        }
    }
}

// Cambiar página de interfaces de red
function changeNetworkPage(direction) {
    const totalPages = Math.ceil(allNetworkInterfaces.length / NETWORKS_PER_PAGE);
    currentNetworkPage += direction;

    if (currentNetworkPage < 1) currentNetworkPage = 1;
    if (currentNetworkPage > totalPages) currentNetworkPage = totalPages;

    updateNetworkInfo(allNetworkInterfaces);
}

// Actualizar alertas
function updateAlerts(alerts) {
    const container = document.getElementById('alertsContainer');
    if (!container) return;

    if (alerts && alerts.length > 0) {
        container.innerHTML = alerts.map(alert => `
            <div class="alert-${alert.severity.toLowerCase()}">
                <strong>${escapeHtml(alert.type)}:</strong> ${escapeHtml(alert.message)}
            </div>
        `).join('');
    } else {
        container.innerHTML = '';
    }
}

// Mostrar error
function showError(message) {
    const container = document.getElementById('alertsContainer');
    if (container) {
        container.innerHTML = `
            <div class="alert-high">
                <strong>Error:</strong> ${escapeHtml(message)}
            </div>
        `;
    }
}

// Utilidades
function escapeHtml(text) {
    const map = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;'
    };
    return String(text).replace(/[&<>"']/g, m => map[m]);
}

function truncateText(text, maxLength) {
    if (text.length <= maxLength) return text;
    return text.substring(0, maxLength) + '...';
}

function formatNumber(num) {
    return new Intl.NumberFormat('es-ES').format(num);
}

// Inicialización
document.addEventListener('DOMContentLoaded', function() {
    console.log('Dashboard inicializado');
    updateDashboard();
    setInterval(updateDashboard, UPDATE_INTERVAL);
});

// Hacer la función global para el onclick
window.changeNetworkPage = changeNetworkPage;

// Recargar al recuperar visibilidad de la pestaña
document.addEventListener('visibilitychange', function() {
    if (!document.hidden) {
        updateDashboard();
    }
});