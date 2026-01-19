// Sistema de Backups - JavaScript
// Diseño compacto con modal de detalles

let currentBackupName = null;
let currentBackupData = null;

// ==================== UTILIDADES ====================

function escapeHtml(text) {
    if (!text) return '';
    const map = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;'
    };
    return String(text).replace(/[&<>"']/g, m => map[m]);
}

function formatDate(dateString) {
    try {
        const date = new Date(dateString);
        if (isNaN(date.getTime())) return 'Fecha no disponible';
        return date.toLocaleDateString('es-ES', {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    } catch (e) {
        return 'Fecha no disponible';
    }
}

function formatDateTime(dateString) {
    try {
        const date = new Date(dateString);
        if (isNaN(date.getTime())) return 'Fecha no disponible';
        return date.toLocaleString('es-ES');
    } catch (e) {
        return 'Fecha no disponible';
    }
}

function formatSize(bytes) {
    if (!bytes || bytes === 0) return '0 MB';
    const mb = bytes / (1024 * 1024);
    return mb.toFixed(2) + ' MB';
}

function truncate(text, maxLength) {
    if (!text) return '';
    if (text.length <= maxLength) return text;
    return text.substring(0, maxLength) + '...';
}

function showAlert(message, type) {
    const container = document.getElementById('alertsContainer');
    const alertClass = type === 'success' ? 'alert-success' : 'alert-error';

    const alert = document.createElement('div');
    alert.className = `alert ${alertClass}`;
    alert.innerHTML = `<span>${message}</span>`;

    container.appendChild(alert);

    setTimeout(() => {
        alert.classList.add('fade-out');
        setTimeout(() => alert.remove(), 300);
    }, 5000);
}

// ==================== CREAR BACKUP ====================

function createBackup() {
    const backupName = document.getElementById('backupName').value.trim();
    const sourcePath = document.getElementById('sourcePath').value.trim();

    if (!backupName || !sourcePath) {
        showAlert('Por favor complete todos los campos', 'error');
        return;
    }

    const progressDiv = document.getElementById('backupProgress');
    progressDiv.style.display = 'block';

    fetch('/api/backups/create', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({
            backupName: backupName,
            sourcePath: sourcePath
        })
    })
    .then(response => response.json())
    .then(data => {
        progressDiv.style.display = 'none';

        if (data.success) {
            showAlert('✓ Backup creado exitosamente: ' + data.backupName, 'success');
            document.getElementById('backupName').value = '';
            document.getElementById('sourcePath').value = '';
            loadBackups();
            loadHistory();
        } else {
            showAlert('✗ Error: ' + data.error, 'error');
        }
    })
    .catch(error => {
        progressDiv.style.display = 'none';
        showAlert('✗ Error al crear backup: ' + error.message, 'error');
    });
}

// ==================== CARGAR BACKUPS (TABLA COMPACTA) ====================

function loadBackups() {
    const tbody = document.getElementById('backupsTableBody');
    const countSpan = document.getElementById('backupCount');

    tbody.innerHTML = '<tr><td colspan="5" class="loading">Cargando backups...</td></tr>';

    fetch('/api/backups/list')
        .then(response => response.json())
        .then(data => {
            if (data.length === 0) {
                tbody.innerHTML = '<tr><td colspan="5" class="loading">No hay backups disponibles</td></tr>';
                countSpan.textContent = '0 backups';
                return;
            }

            countSpan.textContent = `${data.length} backup${data.length !== 1 ? 's' : ''}`;

            tbody.innerHTML = data.map(backup => {
                const statusBadge = backup.isGitRepo
                    ? '<span class="status-badge status-git">✓ Git</span>'
                    : '<span class="status-badge status-no-git">Sin Git</span>';

                // Guardamos los datos como atributo data para usarlos después
                const backupDataStr = encodeURIComponent(JSON.stringify(backup));

                return `
                    <tr class="backup-row">
                        <td>
                            <div class="backup-name-cell">
                                <span class="backup-icon">💾</span>
                                <span class="backup-name-text">${escapeHtml(backup.name)}</span>
                            </div>
                        </td>
                        <td>${formatSize(backup.size)}</td>
                        <td>${formatDate(backup.created)}</td>
                        <td>${statusBadge}</td>
                        <td>
                            <button class="btn-details" onclick="showDetailsModal('${backupDataStr}')">
                                👁️ Ver detalles
                            </button>
                        </td>
                    </tr>
                `;
            }).join('');
        })
        .catch(error => {
            tbody.innerHTML = `<tr><td colspan="5" class="loading">Error: ${error.message}</td></tr>`;
        });
}

// ==================== MODAL DE DETALLES ====================

function showDetailsModal(backupDataStr) {
    try {
        currentBackupData = JSON.parse(decodeURIComponent(backupDataStr));
        currentBackupName = currentBackupData.name;

        // Llenar los datos del modal
        document.getElementById('detailName').textContent = currentBackupData.name;
        document.getElementById('detailSize').textContent = formatSize(currentBackupData.size);
        document.getElementById('detailDate').textContent = formatDate(currentBackupData.created);
        document.getElementById('detailPath').textContent = currentBackupData.path || 'No disponible';

        const statusEl = document.getElementById('detailStatus');
        if (currentBackupData.isGitRepo) {
            statusEl.innerHTML = '<span class="status-badge status-git">✓ Repositorio Git válido</span>';
        } else {
            statusEl.innerHTML = '<span class="status-badge status-no-git">Sin control de versiones</span>';
        }

        document.getElementById('detailsModal').style.display = 'block';
    } catch (e) {
        showAlert('Error al cargar detalles del backup', 'error');
    }
}

function closeDetailsModal() {
    document.getElementById('detailsModal').style.display = 'none';
    currentBackupData = null;
}

// ==================== ACCIONES DESDE MODAL DE DETALLES ====================

function verifyFromDetails() {
    if (!currentBackupName) return;
    closeDetailsModal();
    verifyBackup(currentBackupName);
}

function restoreFromDetails() {
    if (!currentBackupName) return;
    closeDetailsModal();
    showRestoreModal(currentBackupName);
}

function deleteFromDetails() {
    if (!currentBackupName) return;
    closeDetailsModal();
    deleteBackup(currentBackupName);
}

// ==================== VERIFICAR BACKUP ====================

function verifyBackup(backupName) {
    const modal = document.getElementById('verifyModal');
    const modalBody = document.getElementById('verifyModalBody');

    modal.style.display = 'block';
    modalBody.innerHTML = '<div class="loading">Verificando integridad...</div>';

    fetch(`/api/backups/verify/${encodeURIComponent(backupName)}`)
        .then(response => response.json())
        .then(data => {
            if (data.success && data.isValid) {
                modalBody.innerHTML = `
                    <div class="verify-result success">
                        <div class="verify-icon">✓</div>
                        <strong>Backup íntegro</strong>
                        <p>El backup "${escapeHtml(backupName)}" pasó la verificación de integridad.</p>
                    </div>
                    ${data.details ? `<div class="verify-details"><pre>${escapeHtml(data.details)}</pre></div>` : ''}
                `;
            } else if (data.success && !data.isValid) {
                modalBody.innerHTML = `
                    <div class="verify-result error">
                        <div class="verify-icon">✗</div>
                        <strong>Backup corrupto</strong>
                        <p>El backup "${escapeHtml(backupName)}" tiene problemas de integridad.</p>
                    </div>
                    ${data.details ? `<div class="verify-details"><pre>${escapeHtml(data.details)}</pre></div>` : ''}
                `;
            } else {
                modalBody.innerHTML = `
                    <div class="verify-result error">
                        <div class="verify-icon">✗</div>
                        <strong>Error</strong>
                        <p>${escapeHtml(data.error)}</p>
                    </div>
                `;
            }
        })
        .catch(error => {
            modalBody.innerHTML = `
                <div class="verify-result error">
                    <div class="verify-icon">✗</div>
                    <strong>Error</strong>
                    <p>${escapeHtml(error.message)}</p>
                </div>
            `;
        });
}

// ==================== RESTAURAR BACKUP ====================

function showRestoreModal(backupName) {
    currentBackupName = backupName;
    document.getElementById('restoreBackupName').textContent = backupName;
    document.getElementById('targetPath').value = '';
    document.getElementById('restoreModal').style.display = 'block';
}

function confirmRestore() {
    const targetPath = document.getElementById('targetPath').value.trim();

    if (!targetPath) {
        showAlert('Por favor ingrese la ruta de destino', 'error');
        return;
    }

    if (!confirm(`¿Está seguro de restaurar el backup "${currentBackupName}" en "${targetPath}"?\n\n⚠️ Los archivos existentes serán sobrescritos.`)) {
        return;
    }

    fetch('/api/backups/restore', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({
            backupName: currentBackupName,
            targetPath: targetPath
        })
    })
    .then(response => {
        if (response.status === 403) {
            closeRestoreModal();
            showAlert('✗ No tiene permisos para restaurar backups. Solo los administradores pueden hacerlo.', 'error');
            return null;
        }
        if (!response.ok) {
            throw new Error('Error en la respuesta del servidor');
        }
        return response.json();
    })
    .then(data => {
        if (data) {
            closeRestoreModal();

            if (data.success) {
                showAlert('✓ Backup restaurado exitosamente en: ' + data.targetPath, 'success');
                loadHistory();
            } else {
                showAlert('✗ Error: ' + data.error, 'error');
            }
        }
    })
    .catch(error => {
        closeRestoreModal();
        showAlert('✗ Error al restaurar: ' + error.message, 'error');
    });
}

function closeRestoreModal() {
    document.getElementById('restoreModal').style.display = 'none';
}

// ==================== ELIMINAR BACKUP ====================

function deleteBackup(backupName) {
    if (!confirm(`¿Está seguro de eliminar el backup "${backupName}"?\n\nEsta acción no se puede deshacer.`)) {
        return;
    }

    fetch(`/api/backups/${encodeURIComponent(backupName)}`, {
        method: 'DELETE'
    })
    .then(response => {
        if (response.status === 403) {
            showAlert('✗ No tiene permisos para eliminar backups. Solo los administradores pueden hacerlo.', 'error');
            return null;
        }
        if (!response.ok) {
            throw new Error('Error en la respuesta del servidor');
        }
        return response.json();
    })
    .then(data => {
        if (data && data.success) {
            showAlert('✓ Backup eliminado correctamente', 'success');
            loadBackups();
            loadHistory();
        } else if (data && !data.success) {
            showAlert('✗ Error: ' + data.error, 'error');
        }
    })
    .catch(error => {
        showAlert('✗ Error al eliminar: ' + error.message, 'error');
    });
}

// ==================== CARGAR HISTORIAL ====================

function loadHistory() {
    const tbody = document.getElementById('historyTableBody');
    if (!tbody) return;

    tbody.innerHTML = '<tr><td colspan="6" class="loading">Cargando historial...</td></tr>';

    fetch('/api/backups/history')
        .then(response => response.json())
        .then(data => {
            if (data.length === 0) {
                tbody.innerHTML = '<tr><td colspan="6" class="loading">No hay registros en el historial</td></tr>';
                return;
            }

            tbody.innerHTML = data.map(log => {
                const statusClass = log.success ? 'status-success' : 'status-error';
                const statusText = log.success ? '✓ Éxito' : '✗ Error';

                return `
                    <tr>
                        <td>${formatDateTime(log.timestamp)}</td>
                        <td>${escapeHtml(log.name)}</td>
                        <td title="${escapeHtml(log.source || '')}">${truncate(log.source || 'N/A', 25)}</td>
                        <td>${escapeHtml(log.user || 'N/A')}</td>
                        <td>${formatSize(log.size)}</td>
                        <td><span class="${statusClass}">${statusText}</span></td>
                    </tr>
                `;
            }).join('');
        })
        .catch(error => {
            tbody.innerHTML = `<tr><td colspan="6" class="loading">Error: ${error.message}</td></tr>`;
        });
}

// ==================== CERRAR MODALES ====================

function closeVerifyModal() {
    document.getElementById('verifyModal').style.display = 'none';
}

// Cerrar modales al hacer clic fuera
window.onclick = function(event) {
    const modals = ['detailsModal', 'restoreModal', 'verifyModal'];

    modals.forEach(modalId => {
        const modal = document.getElementById(modalId);
        if (event.target === modal) {
            modal.style.display = 'none';
        }
    });
}

// Cerrar con tecla ESC
document.addEventListener('keydown', function(event) {
    if (event.key === 'Escape') {
        closeDetailsModal();
        closeRestoreModal();
        closeVerifyModal();
    }
});

// ==================== INICIALIZACIÓN ====================

document.addEventListener('DOMContentLoaded', function() {
    console.log('Sistema de backups iniciado');
    loadBackups();

    if (document.getElementById('historyTableBody')) {
        loadHistory();
    }
});