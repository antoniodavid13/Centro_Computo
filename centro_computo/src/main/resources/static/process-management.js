// Gestión de Procesos - JavaScript

let currentProcessPid = null;
let searchTimeout = null;

// Ejecutar comando
function executeCommand() {
    const commandInput = document.getElementById('commandInput');
    const command = commandInput.value.trim();

    if (!command) {
        alert('Por favor ingrese un comando');
        return;
    }

    const outputDiv = document.getElementById('commandOutput');
    const outputContent = document.getElementById('outputContent');

    outputDiv.style.display = 'block';
    outputContent.textContent = 'Ejecutando comando...\n';

    fetch('/api/processes/execute', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({
            command: command
        })
    })
    .then(response => {
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        return response.json();
    })
    .then(data => {
        console.log('Respuesta recibida:', data);

        let output = `Comando: ${data.command}\n`;
        output += `Fecha: ${data.timestamp}\n`;
        output += `Estado: ${data.success ? '✓ Éxito' : '✗ Error'}\n`;
        output += `Código de salida: ${data.exitCode !== undefined ? data.exitCode : 'N/A'}\n`;

        if (data.duration) {
            output += `Duración: ${data.duration}\n`;
        }

        output += `\n--- Salida ---\n`;
        output += data.output || 'Sin salida';

        if (data.error && data.error.trim() !== '') {
            output += `\n\n--- Errores ---\n`;
            output += data.error;
        }

        outputContent.textContent = output;

        // Recargar log
        loadCommandLog();
    })
    .catch(error => {
        console.error('Error:', error);
        outputContent.textContent = `Error al ejecutar comando:\n${error.message}\n\nVerifica que:\n1. El servidor está corriendo\n2. La URL es correcta\n3. El comando existe`;
    });
}

// Limpiar salida
function clearOutput() {
    const outputDiv = document.getElementById('commandOutput');
    outputDiv.style.display = 'none';
}

// Buscar procesos
function searchProcesses() {
    const searchInput = document.getElementById('searchInput');
    const query = searchInput.value.trim();
    const resultsDiv = document.getElementById('searchResults');

    if (!query || query.length < 2) {
        resultsDiv.innerHTML = '<p class="info-text">Ingrese al menos 2 caracteres para buscar</p>';
        return;
    }

    // Debounce
    clearTimeout(searchTimeout);
    searchTimeout = setTimeout(() => {
        resultsDiv.innerHTML = '<p class="loading">Buscando...</p>';

        fetch(`/api/processes/search?query=${encodeURIComponent(query)}`)
            .then(response => response.json())
            .then(data => {
                if (data.length === 0) {
                    resultsDiv.innerHTML = '<p class="info-text">No se encontraron procesos</p>';
                    return;
                }

                resultsDiv.innerHTML = data.map(proc => `
                    <div class="process-card">
                        <div class="process-info">
                            <div class="process-name">${escapeHtml(proc.name)} (PID: ${proc.pid})</div>
                            <div class="process-details">
                                CPU: ${proc.cpuUsage.toFixed(1)}% |
                                Memoria: ${proc.memoryUsage.toFixed(0)} MB |
                                Estado: ${proc.status}
                            </div>
                        </div>
                        <div class="process-actions">
                            <button class="btn-details" onclick="showProcessDetails(${proc.pid})">
                                Ver Detalles
                            </button>
                            <button class="btn-kill" onclick="killProcessConfirm(${proc.pid}, '${escapeHtml(proc.name)}')">
                                Terminar
                            </button>
                        </div>
                    </div>
                `).join('');
            })
            .catch(error => {
                resultsDiv.innerHTML = `<p class="info-text">Error: ${error.message}</p>`;
            });
    }, 500);
}

// Mostrar detalles del proceso
function showProcessDetails(pid) {
    currentProcessPid = pid;
    const modal = document.getElementById('processModal');
    const modalBody = document.getElementById('modalBody');

    modalBody.innerHTML = '<p class="loading">Cargando detalles...</p>';
    modal.style.display = 'block';

    fetch(`/api/processes/${pid}`)
        .then(response => response.json())
        .then(data => {
            modalBody.innerHTML = `
                <div class="detail-row">
                    <div class="detail-label">PID:</div>
                    <div class="detail-value">${data.pid}</div>
                </div>
                <div class="detail-row">
                    <div class="detail-label">Nombre:</div>
                    <div class="detail-value">${escapeHtml(data.name)}</div>
                </div>
                <div class="detail-row">
                    <div class="detail-label">Ruta:</div>
                    <div class="detail-value">${escapeHtml(data.path)}</div>
                </div>
                <div class="detail-row">
                    <div class="detail-label">Usuario:</div>
                    <div class="detail-value">${escapeHtml(data.user)}</div>
                </div>
                <div class="detail-row">
                    <div class="detail-label">Estado:</div>
                    <div class="detail-value">${data.state}</div>
                </div>
                <div class="detail-row">
                    <div class="detail-label">CPU:</div>
                    <div class="detail-value">${data.cpuUsage}%</div>
                </div>
                <div class="detail-row">
                    <div class="detail-label">Memoria (RSS):</div>
                    <div class="detail-value">${data.memoryUsage} MB</div>
                </div>
                <div class="detail-row">
                    <div class="detail-label">Memoria Virtual:</div>
                    <div class="detail-value">${data.virtualMemory} MB</div>
                </div>
                <div class="detail-row">
                    <div class="detail-label">Hilos:</div>
                    <div class="detail-value">${data.threadCount}</div>
                </div>
                <div class="detail-row">
                    <div class="detail-label">Tiempo Inicio:</div>
                    <div class="detail-value">${data.startTime}</div>
                </div>
                <div class="detail-row">
                    <div class="detail-label">Tiempo Activo:</div>
                    <div class="detail-value">${data.upTime} segundos</div>
                </div>
                <div class="detail-row">
                    <div class="detail-label">Línea de Comando:</div>
                    <div class="detail-value">${escapeHtml(data.commandLine)}</div>
                </div>
            `;
        })
        .catch(error => {
            modalBody.innerHTML = `<p class="info-text">Error: ${error.message}</p>`;
        });
}

// Cerrar modal
function closeModal() {
    const modal = document.getElementById('processModal');
    modal.style.display = 'none';
    currentProcessPid = null;
}

// Confirmar terminación desde modal
function confirmKillProcess() {
    if (currentProcessPid) {
        if (confirm(`¿Está seguro de terminar el proceso con PID ${currentProcessPid}?`)) {
            killProcess(currentProcessPid);
        }
    }
}

// Confirmar terminación desde búsqueda
function killProcessConfirm(pid, name) {
    if (confirm(`¿Está seguro de terminar el proceso "${name}" (PID: ${pid})?`)) {
        killProcess(pid);
    }
}

// Terminar proceso
function killProcess(pid) {
    fetch(`/api/processes/${pid}`, {
        method: 'DELETE'
    })
    .then(response => response.json())
    .then(data => {
        alert(data.message);

        if (data.success) {
            closeModal();
            // Recargar búsqueda si hay texto
            const searchInput = document.getElementById('searchInput');
            if (searchInput.value.trim()) {
                searchProcesses();
            }
            // Recargar log
            loadCommandLog();
        }
    })
    .catch(error => {
        alert(`Error: ${error.message}`);
    });
}

// Cargar log de comandos
function loadCommandLog() {
    const tbody = document.getElementById('logTableBody');
    tbody.innerHTML = '<tr><td colspan="5" class="loading">Cargando log...</td></tr>';

    fetch('/api/processes/logs?limit=50')
        .then(response => response.json())
        .then(data => {
            if (data.length === 0) {
                tbody.innerHTML = '<tr><td colspan="5" class="info-text">No hay registros en el log</td></tr>';
                return;
            }

            tbody.innerHTML = data.map(log => `
                <tr>
                    <td>${formatDateTime(log.timestamp)}</td>
                    <td>${escapeHtml(log.user)}</td>
                    <td><span class="log-command">${escapeHtml(log.command)}</span></td>
                    <td class="${log.success ? 'status-success' : 'status-error'}">
                        ${log.success ? '✓ Éxito' : '✗ Error'}
                    </td>
                    <td class="log-output" title="${escapeHtml(log.output)}">
                        ${escapeHtml(log.output.substring(0, 50))}${log.output.length > 50 ? '...' : ''}
                    </td>
                </tr>
            `).join('');
        })
        .catch(error => {
            tbody.innerHTML = `<tr><td colspan="5" class="info-text">Error: ${error.message}</td></tr>`;
        });
}

// Limpiar log
function clearCommandLog() {
    if (!confirm('¿Está seguro de limpiar todo el log de comandos?')) {
        return;
    }

    fetch('/api/processes/logs', {
        method: 'DELETE'
    })
    .then(response => response.json())
    .then(data => {
        alert(data.message);
        loadCommandLog();
    })
    .catch(error => {
        alert(`Error: ${error.message}`);
    });
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

function formatDateTime(dateString) {
    const date = new Date(dateString);
    return date.toLocaleString('es-ES');
}

// Cerrar modal al hacer clic fuera
window.onclick = function(event) {
    const modal = document.getElementById('processModal');
    if (event.target === modal) {
        closeModal();
    }
}

// Cargar log al iniciar
document.addEventListener('DOMContentLoaded', function() {
    console.log('Módulo de gestión de procesos cargado');
    loadCommandLog();
});