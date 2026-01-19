package com.antovdv.centro_computo.controller;

import com.antovdv.centro_computo.model.UserSession;
import com.antovdv.centro_computo.service.TicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets")
@CrossOrigin(origins = "*")
public class Ticketcontroller {

    @Autowired
    private TicketService ticketService;

    /**
     * Crear un nuevo ticket - TODOS los usuarios autenticados
     */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createTicket(
            @RequestBody Map<String, String> request,
            HttpSession session) {

        UserSession userSession = (UserSession) session.getAttribute("userSession");

        if (userSession == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "No autenticado");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        String title = request.get("title");
        String description = request.get("description");
        String category = request.getOrDefault("category", "OTRO");
        String priority = request.getOrDefault("priority", "MEDIA");

        if (title == null || title.trim().isEmpty() ||
                description == null || description.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "El título y la descripción son obligatorios");
            return ResponseEntity.badRequest().body(error);
        }

        Map<String, Object> result = ticketService.createTicket(
                title, description, category, priority,
                userSession.getEmail(), userSession.getName()
        );

        return ResponseEntity.ok(result);
    }

    /**
     * Obtener lista de tickets con filtros
     */
    @GetMapping("/list")
    public ResponseEntity<?> getTickets(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String assignedTo,
            @RequestParam(required = false) String createdBy,
            HttpSession session) {

        UserSession userSession = (UserSession) session.getAttribute("userSession");

        if (userSession == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<Map<String, Object>> tickets = ticketService.getTickets(
                status, priority, category, assignedTo, createdBy,
                userSession.getEmail(), userSession.getUserType()
        );

        return ResponseEntity.ok(tickets);
    }

    /**
     * Obtener un ticket por ID
     */
    @GetMapping("/{ticketId}")
    public ResponseEntity<?> getTicket(
            @PathVariable int ticketId,
            HttpSession session) {

        UserSession userSession = (UserSession) session.getAttribute("userSession");

        if (userSession == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Map<String, Object> ticket = ticketService.getTicketById(ticketId);

        if (ticket == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Ticket no encontrado");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        // CLIENTE solo puede ver sus propios tickets
        if (userSession.isClient()) {
            String createdBy = (String) ticket.get("createdByEmail");
            if (!userSession.getEmail().equals(createdBy)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        return ResponseEntity.ok(ticket);
    }

    /**
     * Asignar ticket a un técnico - ADMIN y TECNICO
     */
    @PostMapping("/{ticketId}/assign")
    public ResponseEntity<Map<String, Object>> assignTicket(
            @PathVariable int ticketId,
            @RequestBody Map<String, String> request,
            HttpSession session) {

        UserSession userSession = (UserSession) session.getAttribute("userSession");

        if (userSession == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "No autenticado");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        if (!userSession.isAdmin() && !userSession.isTechnician()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "No tiene permisos para asignar tickets");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }

        String technicianEmail = request.get("technicianEmail");
        String technicianName = request.get("technicianName");

        if (technicianEmail == null || technicianEmail.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Debe especificar un técnico");
            return ResponseEntity.badRequest().body(error);
        }

        Map<String, Object> result = ticketService.assignTicket(
                ticketId, technicianEmail, technicianName,
                userSession.getEmail(), userSession.getName()
        );

        return ResponseEntity.ok(result);
    }

    /**
     * Cambiar estado del ticket - ADMIN y TECNICO
     */
    @PutMapping("/{ticketId}/status")
    public ResponseEntity<Map<String, Object>> changeStatus(
            @PathVariable int ticketId,
            @RequestBody Map<String, String> request,
            HttpSession session) {

        UserSession userSession = (UserSession) session.getAttribute("userSession");

        if (userSession == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "No autenticado");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        String newStatus = request.get("status");

        // CLIENTE solo puede cerrar sus propios tickets resueltos
        if (userSession.isClient()) {
            if (!"CERRADO".equals(newStatus)) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "Solo puede cerrar tickets resueltos");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
            }

            Map<String, Object> ticket = ticketService.getTicketById(ticketId);
            if (ticket == null || !userSession.getEmail().equals(ticket.get("createdByEmail"))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            if (!"RESUELTO".equals(ticket.get("status"))) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "Solo puede cerrar tickets que estén resueltos");
                return ResponseEntity.badRequest().body(error);
            }
        }

        Map<String, Object> result = ticketService.changeStatus(
                ticketId, newStatus, userSession.getEmail(), userSession.getName()
        );

        return ResponseEntity.ok(result);
    }

    /**
     * Cambiar prioridad del ticket - ADMIN y TECNICO
     */
    @PutMapping("/{ticketId}/priority")
    public ResponseEntity<Map<String, Object>> changePriority(
            @PathVariable int ticketId,
            @RequestBody Map<String, String> request,
            HttpSession session) {

        UserSession userSession = (UserSession) session.getAttribute("userSession");

        if (userSession == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "No autenticado");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        if (!userSession.isAdmin() && !userSession.isTechnician()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "No tiene permisos para cambiar la prioridad");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }

        String newPriority = request.get("priority");

        Map<String, Object> result = ticketService.changePriority(
                ticketId, newPriority, userSession.getEmail(), userSession.getName()
        );

        return ResponseEntity.ok(result);
    }

    /**
     * Agregar comentario a un ticket - TODOS
     */
    @PostMapping("/{ticketId}/comments")
    public ResponseEntity<Map<String, Object>> addComment(
            @PathVariable int ticketId,
            @RequestBody Map<String, Object> request,
            HttpSession session) {

        UserSession userSession = (UserSession) session.getAttribute("userSession");

        if (userSession == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "No autenticado");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        String comment = (String) request.get("comment");
        Boolean isInternal = (Boolean) request.getOrDefault("isInternal", false);

        // CLIENTE no puede hacer comentarios internos
        if (userSession.isClient()) {
            isInternal = false;

            Map<String, Object> ticket = ticketService.getTicketById(ticketId);
            if (ticket == null || !userSession.getEmail().equals(ticket.get("createdByEmail"))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        if (comment == null || comment.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "El comentario no puede estar vacío");
            return ResponseEntity.badRequest().body(error);
        }

        Map<String, Object> result = ticketService.addComment(
                ticketId, comment, isInternal,
                userSession.getEmail(), userSession.getName()
        );

        return ResponseEntity.ok(result);
    }

    /**
     * Obtener comentarios de un ticket
     */
    @GetMapping("/{ticketId}/comments")
    public ResponseEntity<?> getComments(
            @PathVariable int ticketId,
            HttpSession session) {

        UserSession userSession = (UserSession) session.getAttribute("userSession");

        if (userSession == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        boolean includeInternal = !userSession.isClient();

        if (userSession.isClient()) {
            Map<String, Object> ticket = ticketService.getTicketById(ticketId);
            if (ticket == null || !userSession.getEmail().equals(ticket.get("createdByEmail"))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        List<Map<String, Object>> comments = ticketService.getComments(ticketId, includeInternal);
        return ResponseEntity.ok(comments);
    }

    /**
     * Obtener historial de un ticket - ADMIN y TECNICO
     */
    @GetMapping("/{ticketId}/history")
    public ResponseEntity<?> getHistory(
            @PathVariable int ticketId,
            HttpSession session) {

        UserSession userSession = (UserSession) session.getAttribute("userSession");

        if (userSession == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!userSession.isAdmin() && !userSession.isTechnician()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<Map<String, Object>> history = ticketService.getTicketHistory(ticketId);
        return ResponseEntity.ok(history);
    }

    /**
     * Obtener estadísticas de tickets - ADMIN y TECNICO
     */
    @GetMapping("/statistics")
    public ResponseEntity<?> getStatistics(HttpSession session) {
        UserSession userSession = (UserSession) session.getAttribute("userSession");

        if (userSession == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!userSession.isAdmin() && !userSession.isTechnician()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Map<String, Object> stats = ticketService.getTicketStatistics();
        return ResponseEntity.ok(stats);
    }

    /**
     * Obtener lista de técnicos para asignación - ADMIN y TECNICO
     */
    @GetMapping("/technicians")
    public ResponseEntity<?> getTechnicians(HttpSession session) {
        UserSession userSession = (UserSession) session.getAttribute("userSession");

        if (userSession == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!userSession.isAdmin() && !userSession.isTechnician()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<Map<String, Object>> technicians = ticketService.getTechnicians();
        return ResponseEntity.ok(technicians);
    }
}