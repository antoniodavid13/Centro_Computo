// Sistema de Tickets de Soporte - JavaScript

let currentTicketId = null;

// ==================== UTILIDADES ====================

function escapeHtml(text) {
    if (!text) return '';
    const map = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' };
    return String(text).replace(/[&<>"']/g, m => map[m]);
}

function formatDateTime(dateString) {
    if (!dateString) return 'N/A';
    try {
        const date = new Date(dateString);
        if (isNaN(date.getTime())) return 'N/A';
        return date.toLocaleString('es-ES', {
            year: 'numeric', month: 'short', day: 'numeric',
            hour: '2-digit', minute: '2-digit'
        });
    } catch (e) { return 'N/A'; }
}

function showAlert(message, type) {
    const container = document.getElementById('alertsContainer');
    const alert = document.createElement('div');
    alert.className = `alert ${type === 'success' ? 'alert-success' : 'alert-error'}`;
    alert.innerHTML = `<span>${message}</span>`;
    container.appendChild(alert);
    setTimeout(() => {
        alert.classList.add('fade-out');
        setTimeout(() => alert.remove(), 300);
    }, 5000);
}

// ==================== BADGES ====================

function getPriorityBadge(priority) {
    const badges = {
        'CRITICA': '<span class="badge badge-critical">🔴 Crítica</span>',
        'ALTA': '<span class="badge badge-high">🟠 Alta</span>',
        'MEDIA': '<span class="badge badge-medium">🟡 Media</span>',
        'BAJA': '<span class="badge badge-low">🟢 Baja</span>'
    };
    return badges[priority] || priority;
}

function getStatusBadge(status) {
    const badges = {
        'ABIERTO': '<span class="badge badge-open">🔵 Abierto</span>',
        'EN_PROGRESO': '<span class="badge badge-progress">🟡 En Progreso</span>',
        'PENDIENTE': '<span class="badge badge-pending">🟠 Pendiente</span>',
        'RESUELTO': '<span class="badge badge-resolved">🟢 Resuelto</span>',
        'CERRADO': '<span class="badge badge-closed">⚫ Cerrado</span>'
    };
    return badges[status] || status;
}

function getCategoryIcon(category) {
    const icons = { 'HARDWARE': '🖥️', 'SOFTWARE': '💿', 'RED': '🌐', 'SEGURIDAD': '🔒', 'OTRO': '📎' };
    return icons[category] || '📎';
}

function getHistoryIcon(action) {
    const icons = { 'CREATED': '🆕', 'STATUS_CHANGED': '📝', 'ASSIGNED': '👤', 'PRIORITY_CHANGED': '⚡', 'COMMENT_ADDED': '💬' };
    return icons[action] || '📌';
}

// ==================== ESTADÍSTICAS ====================

function loadStatistics() {
    if (currentUserType === 'CLIENTE') return;

    fetch('/api/tickets/statistics')
        .then(response => response.json())
        .then(data => {
            document.getElementById('statTotal').textContent = data.total || 0;
            document.getElementById('statOpen').textContent = data.byStatus?.ABIERTO || 0;
            document.getElementById('statProgress').textContent = data.byStatus?.EN_PROGRESO || 0;
            document.getElementById('statCritical').textContent = data.byPriority?.CRITICA || 0;
            document.getElementById('statUnassigned').textContent = data.unassigned || 0;
        })
        .catch(error => console.error('Error cargando estadísticas:', error));
}

// ==================== CREAR TICKET ====================

function createTicket() {
    const title = document.getElementById('ticketTitle').value.trim();
    const description = document.getElementById('ticketDescription').value.trim();
    const category = document.getElementById('ticketCategory').value;
    const priority = document.getElementById('ticketPriority').value;

    if (!title || !description) {
        showAlert('El título y la descripción son obligatorios', 'error');
        return;
    }

    fetch('/api/tickets/create', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ title, description, category, priority })
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showAlert(`✓ Ticket creado: ${data.ticketNumber}`, 'success');
            document.getElementById('ticketTitle').value = '';
            document.getElementById('ticketDescription').value = '';
            loadTickets();
            loadStatistics();
        } else {
            showAlert('✗ Error: ' + data.error, 'error');
        }
    })
    .catch(error => showAlert('✗ Error: ' + error.message, 'error'));
}

// ==================== CARGAR TICKETS ====================

function loadTickets() {
    const tbody = document.getElementById('ticketsTableBody');
    tbody.innerHTML = '<tr><td colspan="9" class="loading">Cargando tickets...</td></tr>';

    const status = document.getElementById('filterStatus').value;
    const priority = document.getElementById('filterPriority').value;
    const category = document.getElementById('filterCategory').value;

    let url = '/api/tickets/list?';
    if (status) url += `status=${status}&`;
    if (priority) url += `priority=${priority}&`;
    if (category) url += `category=${category}&`;

    fetch(url)
        .then(response => response.json())
        .then(data => {
            if (data.length === 0) {
                tbody.innerHTML = '<tr><td colspan="9" class="loading">No hay tickets</td></tr>';
                return;
            }

            tbody.innerHTML = data.map(ticket => {
                const canManage = currentUserType !== 'CLIENTE';

                let actions = `<button class="btn-action-sm btn-view" onclick="viewTicket(${ticket.id})">👁️</button>`;

                if (canManage) {
                    actions += `
                        <button class="btn-action-sm btn-assign" onclick="showAssignModal(${ticket.id}, '${escapeHtml(ticket.ticketNumber)}')">👤</button>
                        <button class="btn-action-sm btn-status" onclick="showStatusModal(${ticket.id}, '${escapeHtml(ticket.ticketNumber)}', '${ticket.status}')">📝</button>
                    `;
                }

                return `
                    <tr class="ticket-row priority-${ticket.priority.toLowerCase()}">
                        <td class="ticket-number">${escapeHtml(ticket.ticketNumber)}</td>
                        <td class="ticket-title">
                            ${escapeHtml(ticket.title)}
                            ${ticket.commentCount > 0 ? `<span class="comment-count">💬 ${ticket.commentCount}</span>` : ''}
                        </td>
                        <td>${getCategoryIcon(ticket.category)} ${ticket.category}</td>
                        <td>${getPriorityBadge(ticket.priority)}</td>
                        <td>${getStatusBadge(ticket.status)}</td>
                        <td>${escapeHtml(ticket.createdByName || ticket.createdByEmail)}</td>
                        <td>${ticket.assignedToName ? escapeHtml(ticket.assignedToName) : '<span class="unassigned">Sin asignar</span>'}</td>
                        <td>${formatDateTime(ticket.createdAt)}</td>
                        <td class="actions-cell">${actions}</td>
                    </tr>
                `;
            }).join('');
        })
        .catch(error => {
            tbody.innerHTML = `<tr><td colspan="9" class="loading">Error: ${error.message}</td></tr>`;
        });
}

// ==================== VER TICKET ====================

function viewTicket(ticketId) {
    currentTicketId = ticketId;
    const modal = document.getElementById('viewTicketModal');
    const body = document.getElementById('ticketDetailBody');

    modal.style.display = 'block';
    body.innerHTML = '<div class="loading">Cargando detalles...</div>';

    fetch(`/api/tickets/${ticketId}`)
        .then(response => response.json())
        .then(ticket => {
            return fetch(`/api/tickets/${ticketId}/comments`)
                .then(response => response.json())
                .then(comments => renderTicketDetail(ticket, comments));
        })
        .catch(error => {
            body.innerHTML = `<div class="error">Error: ${error.message}</div>`;
        });
}

function renderTicketDetail(ticket, comments) {
    const body = document.getElementById('ticketDetailBody');
    const canManage = currentUserType !== 'CLIENTE';

    let historySection = '';
    if (canManage) {
        historySection = `
            <div class="detail-section">
                <h4>📜 Historial de Cambios</h4>
                <div id="ticketHistoryContainer" class="history-container">
                    <div class="loading">Cargando historial...</div>
                </div>
            </div>
        `;
        setTimeout(() => loadTicketHistory(ticket.id), 100);
    }

    body.innerHTML = `
        <div class="ticket-detail">
            <div class="detail-header">
                <div class="detail-ticket-number">${escapeHtml(ticket.ticketNumber)}</div>
                <div class="detail-badges">
                    ${getPriorityBadge(ticket.priority)}
                    ${getStatusBadge(ticket.status)}
                </div>
            </div>

            <div class="detail-title">${escapeHtml(ticket.title)}</div>

            <div class="detail-meta">
                <div class="meta-item">
                    <span class="meta-label">Categoría:</span>
                    <span class="meta-value">${getCategoryIcon(ticket.category)} ${ticket.category}</span>
                </div>
                <div class="meta-item">
                    <span class="meta-label">Creado por:</span>
                    <span class="meta-value">${escapeHtml(ticket.createdByName)}</span>
                </div>
                <div class="meta-item">
                    <span class="meta-label">Asignado a:</span>
                    <span class="meta-value">${ticket.assignedToName ? escapeHtml(ticket.assignedToName) : '<span class="unassigned">Sin asignar</span>'}</span>
                </div>
                <div class="meta-item">
                    <span class="meta-label">Fecha:</span>
                    <span class="meta-value">${formatDateTime(ticket.createdAt)}</span>
                </div>
            </div>

            <div class="detail-section">
                <h4>📝 Descripción</h4>
                <div class="detail-description">${escapeHtml(ticket.description)}</div>
            </div>

            <div class="detail-section">
                <h4>💬 Comentarios (${comments.length})</h4>
                <div class="comments-list">
                    ${comments.length === 0 ? '<p class="no-comments">No hay comentarios aún</p>' :
                        comments.map(comment => `
                            <div class="comment ${comment.isInternal ? 'comment-internal' : ''}">
                                <div class="comment-header">
                                    <span class="comment-author">${escapeHtml(comment.userName)}</span>
                                    <span class="comment-date">${formatDateTime(comment.createdAt)}</span>
                                    ${comment.isInternal ? '<span class="comment-internal-badge">Interno</span>' : ''}
                                </div>
                                <div class="comment-body">${escapeHtml(comment.comment)}</div>
                            </div>
                        `).join('')
                    }
                </div>

                <div class="add-comment">
                    <textarea id="newComment" class="form-textarea" rows="3" placeholder="Escriba un comentario..."></textarea>
                    ${canManage ? `
                        <label class="checkbox-label">
                            <input type="checkbox" id="commentInternal"> Comentario interno
                        </label>
                    ` : ''}
                    <button onclick="addComment(${ticket.id})" class="btn-add-comment">💬 Agregar Comentario</button>
                </div>
            </div>

            ${historySection}
        </div>
    `;
}

function loadTicketHistory(ticketId) {
    const container = document.getElementById('ticketHistoryContainer');
    if (!container) return;

    fetch(`/api/tickets/${ticketId}/history`)
        .then(response => response.json())
        .then(history => {
            if (history.length === 0) {
                container.innerHTML = '<p class="no-history">No hay historial</p>';
                return;
            }
            container.innerHTML = history.map(entry => `
                <div class="history-entry">
                    <div class="history-icon">${getHistoryIcon(entry.action)}</div>
                    <div class="history-content">
                        <div class="history-description">${escapeHtml(entry.description)}</div>
                        <div class="history-meta">
                            <span>${escapeHtml(entry.userName)}</span> •
                            <span>${formatDateTime(entry.createdAt)}</span>
                        </div>
                    </div>
                </div>
            `).join('');
        })
        .catch(error => {
            container.innerHTML = `<p class="error">Error: ${error.message}</p>`;
        });
}

// ==================== AGREGAR COMENTARIO ====================

function addComment(ticketId) {
    const comment = document.getElementById('newComment').value.trim();
    const isInternalEl = document.getElementById('commentInternal');
    const isInternal = isInternalEl ? isInternalEl.checked : false;

    if (!comment) {
        showAlert('El comentario no puede estar vacío', 'error');
        return;
    }

    fetch(`/api/tickets/${ticketId}/comments`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ comment, isInternal })
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showAlert('✓ Comentario agregado', 'success');
            viewTicket(ticketId);
            loadTickets();
        } else {
            showAlert('✗ Error: ' + data.error, 'error');
        }
    })
    .catch(error => showAlert('✗ Error: ' + error.message, 'error'));
}

// ==================== ASIGNAR TICKET ====================

function showAssignModal(ticketId, ticketNumber) {
    currentTicketId = ticketId;
    document.getElementById('assignTicketNumber').textContent = ticketNumber;
    document.getElementById('assignModal').style.display = 'block';

    const select = document.getElementById('assignTechnician');
    select.innerHTML = '<option value="">Cargando...</option>';

    fetch('/api/tickets/technicians')
        .then(response => response.json())
        .then(data => {
            select.innerHTML = '<option value="">Seleccione un técnico</option>' +
                data.map(tech => `<option value="${tech.email}" data-name="${escapeHtml(tech.name)}">${escapeHtml(tech.name)}</option>`).join('');
        })
        .catch(() => select.innerHTML = '<option value="">Error</option>');
}

function confirmAssign() {
    const select = document.getElementById('assignTechnician');
    const technicianEmail = select.value;
    const technicianName = select.options[select.selectedIndex].dataset.name;

    if (!technicianEmail) {
        showAlert('Seleccione un técnico', 'error');
        return;
    }

    fetch(`/api/tickets/${currentTicketId}/assign`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ technicianEmail, technicianName })
    })
    .then(response => response.json())
    .then(data => {
        closeAssignModal();
        if (data.success) {
            showAlert('✓ Ticket asignado', 'success');
            loadTickets();
            loadStatistics();
        } else {
            showAlert('✗ Error: ' + data.error, 'error');
        }
    })
    .catch(error => {
        closeAssignModal();
        showAlert('✗ Error: ' + error.message, 'error');
    });
}

function closeAssignModal() {
    document.getElementById('assignModal').style.display = 'none';
    currentTicketId = null;
}

// ==================== CAMBIAR ESTADO ====================

function showStatusModal(ticketId, ticketNumber, currentStatus) {
    currentTicketId = ticketId;
    document.getElementById('statusTicketNumber').textContent = ticketNumber;
    document.getElementById('newStatus').value = currentStatus;
    document.getElementById('statusModal').style.display = 'block';
}

function confirmStatusChange() {
    const newStatus = document.getElementById('newStatus').value;

    fetch(`/api/tickets/${currentTicketId}/status`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: newStatus })
    })
    .then(response => response.json())
    .then(data => {
        closeStatusModal();
        if (data.success) {
            showAlert('✓ Estado actualizado', 'success');
            loadTickets();
            loadStatistics();
        } else {
            showAlert('✗ Error: ' + data.error, 'error');
        }
    })
    .catch(error => {
        closeStatusModal();
        showAlert('✗ Error: ' + error.message, 'error');
    });
}

function closeStatusModal() {
    document.getElementById('statusModal').style.display = 'none';
    currentTicketId = null;
}

// ==================== CERRAR MODALES ====================

function closeViewModal() {
    document.getElementById('viewTicketModal').style.display = 'none';
    currentTicketId = null;
}

window.onclick = function(event) {
    ['viewTicketModal', 'assignModal', 'statusModal'].forEach(id => {
        const modal = document.getElementById(id);
        if (event.target === modal) modal.style.display = 'none';
    });
}

document.addEventListener('keydown', function(event) {
    if (event.key === 'Escape') {
        closeViewModal();
        closeAssignModal();
        closeStatusModal();
    }
});

// ==================== INICIALIZACIÓN ====================

document.addEventListener('DOMContentLoaded', function() {
    console.log('Sistema de tickets iniciado - Usuario:', currentUserType);
    loadTickets();
    loadStatistics();
});