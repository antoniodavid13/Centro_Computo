// Gestión de Base de Datos - JavaScript

// Cargar estadísticas
function loadStatistics() {
    fetch('/api/database/statistics')
        .then(response => response.json())
        .then(data => {
            // Conexiones
            document.getElementById('activeConnections').textContent = data.activeConnections || '0';
            document.getElementById('maxConnections').textContent = `de ${data.maxConnections || '0'}`;

            // Consultas
            const qps = parseFloat(data.queriesPerSecond || 0).toFixed(2);
            document.getElementById('queriesPerSecond').textContent = qps;
            document.getElementById('slowQueries').textContent = `${data.slowQueries || 0} lentas`;

            // Base de datos
            const sizeInMB = ((data.databaseSize || 0) / (1024 * 1024)).toFixed(2);
            document.getElementById('databaseSize').textContent = sizeInMB + ' MB';
            document.getElementById('tableCount').textContent = `${data.tableCount || 0} tablas`;

            // Cache
            const cacheRatio = parseFloat(data.cacheHitRatio || 0).toFixed(2);
            document.getElementById('cacheHitRatio').textContent = cacheRatio + '%';

            // Actualizar timestamp
            document.getElementById('lastUpdate').textContent =
                'Última actualización: ' + new Date().toLocaleTimeString('es-ES');

            // Cargar conexiones
            loadConnections();
        })
        .catch(error => {
            console.error('Error cargando estadísticas:', error);
            showAlert('Error al cargar estadísticas: ' + error.message, 'error');
        });
}

// Cargar conexiones activas
function loadConnections() {
    const tbody = document.getElementById('connectionsTableBody');
    if (!tbody) return; // Si no existe (CLIENTE), salir

    tbody.innerHTML = '<tr><td colspan="7" class="loading">Cargando conexiones...</td></tr>';

    fetch('/api/database/connections')
        .then(response => response.json())
        .then(data => {
            if (data.length === 0) {
                tbody.innerHTML = '<tr><td colspan="7" class="loading">No hay conexiones activas</td></tr>';
                return;
            }

            tbody.innerHTML = data.map(conn => `
                <tr>
                    <td>${conn.id}</td>
                    <td>${escapeHtml(conn.user)}</td>
                    <td>${escapeHtml(conn.host)}</td>
                    <td>${escapeHtml(conn.database || 'NULL')}</td>
                    <td>${escapeHtml(conn.command)}</td>
                    <td>${conn.time}</td>
                    <td>${escapeHtml(conn.state || 'N/A')}</td>
                </tr>
            `).join('');
        })
        .catch(error => {
            tbody.innerHTML = `<tr><td colspan="7" class="loading">Error: ${error.message}</td></tr>`;
        });
}

// Ejecutar consulta SQL
function executeQuery() {
    const query = document.getElementById('queryEditor').value.trim();
    const timeout = parseInt(document.getElementById('queryTimeout').value);
    const resultsDiv = document.getElementById('queryResults');
    const resultsContent = document.getElementById('resultsContent');
    const resultsInfo = document.getElementById('resultsInfo');

    if (!query) {
        showAlert('Por favor ingrese una consulta SQL', 'error');
        return;
    }

    resultsDiv.style.display = 'block';
    resultsContent.innerHTML = '<div class="loading">Ejecutando consulta...</div>';
    resultsInfo.textContent = '';
    resultsInfo.className = 'results-info';

    fetch('/api/database/execute-query', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({
            query: query,
            timeout: timeout
        })
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            if (data.type === 'SELECT') {
                // Mostrar resultados de SELECT
                resultsInfo.textContent = `${data.rowCount} fila(s) en ${data.executionTime}ms`;
                resultsInfo.className = 'results-info';

                if (data.rows.length === 0) {
                    resultsContent.innerHTML = '<div class="results-success">Consulta exitosa, sin resultados</div>';
                } else {
                    renderResultsTable(data.columns, data.rows, resultsContent);
                }
            } else {
                // Resultado de UPDATE/INSERT/DELETE
                resultsInfo.textContent = `${data.rowsAffected} fila(s) afectada(s) en ${data.executionTime}ms`;
                resultsInfo.className = 'results-info';
                resultsContent.innerHTML = `
                    <div class="results-success">
                        ✓ Consulta ejecutada exitosamente<br>
                        Filas afectadas: ${data.rowsAffected}<br>
                        Tiempo de ejecución: ${data.executionTime}ms
                    </div>
                `;
            }
        } else {
            resultsInfo.textContent = 'Error';
            resultsInfo.className = 'results-info error';
            resultsContent.innerHTML = `
                <div class="results-error">
                    <strong>✗ Error en la consulta:</strong><br>
                    ${escapeHtml(data.error)}
                </div>
            `;
        }
    })
    .catch(error => {
        resultsInfo.textContent = 'Error';
        resultsInfo.className = 'results-info error';
        resultsContent.innerHTML = `
            <div class="results-error">
                <strong>✗ Error de conexión:</strong><br>
                ${escapeHtml(error.message)}
            </div>
        `;
    });
}

// Renderizar tabla de resultados
function renderResultsTable(columns, rows, container) {
    let html = '<table class="results-table"><thead><tr>';

    // Headers
    columns.forEach(col => {
        html += `<th>${escapeHtml(col)}</th>`;
    });
    html += '</tr></thead><tbody>';

    // Rows
    rows.forEach(row => {
        html += '<tr>';
        columns.forEach(col => {
            const value = row[col];
            html += `<td>${value !== null && value !== undefined ? escapeHtml(String(value)) : '<em>NULL</em>'}</td>`;
        });
        html += '</tr>';
    });

    html += '</tbody></table>';
    container.innerHTML = html;
}

// Limpiar editor
function clearQueryEditor() {
    document.getElementById('queryEditor').value = '';
    document.getElementById('queryResults').style.display = 'none';
}

// Crear respaldo de estructura
function createStructureBackup() {
    const dbName = document.getElementById('dbName').value.trim();
    const backupName = document.getElementById('structureBackupName').value.trim();

    if (!dbName || !backupName) {
        showAlert('Por favor complete todos los campos', 'error');
        return;
    }

    showAlert('Creando respaldo de estructura...', 'info');

    fetch('/api/database/backup-structure', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({
            databaseName: dbName,
            backupName: backupName
        })
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showAlert(`✓ Respaldo creado exitosamente: ${data.backupName}`, 'success');
            document.getElementById('structureBackupName').value = '';
        } else {
            showAlert(`✗ Error: ${data.error}`, 'error');
        }
    })
    .catch(error => {
        showAlert(`✗ Error: ${error.message}`, 'error');
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

function showAlert(message, type) {
    const container = document.getElementById('alertsContainer');
    const alertClass = type === 'success' ? 'alert-high' :
                      type === 'error' ? 'alert-medium' :
                      'alert-medium';

    const alert = document.createElement('div');
    alert.className = alertClass;
    alert.textContent = message;

    container.appendChild(alert);

    setTimeout(() => {
        alert.remove();
    }, 5000);
}

// Inicialización
document.addEventListener('DOMContentLoaded', function() {
    console.log('Gestión de Base de Datos iniciada');
    loadStatistics();

    // Auto-refresh cada 30 segundos
    setInterval(() => {
        loadStatistics();
    }, 30000);

    // Enter en el editor ejecuta la consulta
    const editor = document.getElementById('queryEditor');
    if (editor) {
        editor.addEventListener('keydown', function(e) {
            if (e.ctrlKey && e.key === 'Enter') {
                executeQuery();
            }
        });
    }
});