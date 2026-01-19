package com.antovdv.centro_computo;

import com.antovdv.centro_computo.model.UserSession;
import com.antovdv.centro_computo.service.SystemMonitorService;
import com.antovdv.centro_computo.service.AlertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import jakarta.servlet.http.HttpSession;

@Controller
public class Login {

    private LoginManager loginManager = new LoginManager();

    @Autowired
    private SystemMonitorService monitorService;

    @Autowired
    private AlertService alertService;

    @GetMapping("/")
    public String indexRoot() {
        return "index";
    }

    @GetMapping("/login")
    public String indexLogin(HttpSession session) {
        // Si ya está logueado, redirigir al dashboard
        if (session.getAttribute("userSession") != null) {
            return "redirect:/monitoreo";
        }
        return "index";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @PostMapping("/api/register")
    public ModelAndView registerUser(
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String name,
            @RequestParam String tipo
    ) {
        ModelAndView mav = new ModelAndView();
        if (loginManager.usuarioExiste(email)) {
            mav.setViewName("register");
            mav.addObject("error", "El correo electrónico ya está registrado.");
            return mav;
        }

        boolean registered = loginManager.registrarUsuario(email, password, name, tipo);

        if (registered) {
            mav.setViewName("redirect:/login");
            mav.addObject("success", "Registro exitoso. Por favor, inicie sesión.");
        } else {
            mav.setViewName("register");
            mav.addObject("error", "Error al registrar usuario. Intente nuevamente.");
        }

        return mav;
    }

    @PostMapping("/api/login")
    public ModelAndView login(
            @RequestParam String email,
            @RequestParam String password,
            HttpSession session) {
        ModelAndView mav = new ModelAndView();

        if (loginManager.verificarCredenciales(email, password)) {
            // Obtener información del usuario
            UserSession userSession = loginManager.getUserSession(email);

            if (userSession != null) {
                // Guardar en sesión
                session.setAttribute("userSession", userSession);

                mav.setViewName("redirect:/monitoreo");
                return mav;
            }
        }

        mav.setViewName("index");
        mav.addObject("error", "Credenciales incorrectas");
        return mav;
    }

    @GetMapping("/monitoreo")
    public String monitoreo(HttpSession session, Model model) {
        UserSession userSession = (UserSession) session.getAttribute("userSession");

        if (userSession == null) {
            return "redirect:/login";
        }

        model.addAttribute("userName", userSession.getName());
        model.addAttribute("userType", userSession.getUserType());
        model.addAttribute("userSession", userSession);
        return "dashboard";
    }

    @GetMapping("/process-management")
    public String processManagement(HttpSession session, Model model) {
        UserSession userSession = (UserSession) session.getAttribute("userSession");

        // Verificar que el usuario esté logueado
        if (userSession == null) {
            return "redirect:/login";
        }

        // Ahora todos pueden acceder (ADMIN, TECNICO, CLIENTE)
        model.addAttribute("userName", userSession.getName());
        model.addAttribute("userType", userSession.getUserType());
        model.addAttribute("userSession", userSession);
        return "process-management";
    }


    @GetMapping("/backups")
    public String backups(HttpSession session, Model model) {
        UserSession userSession = (UserSession) session.getAttribute("userSession");

        if (userSession == null) {
            return "redirect:/login";
        }

        model.addAttribute("userName", userSession.getName());
        model.addAttribute("userType", userSession.getUserType());
        model.addAttribute("userSession", userSession);
        return "backups";
    }

    @GetMapping("/database")
    public String database(HttpSession session, Model model) {
        UserSession userSession = (UserSession) session.getAttribute("userSession");

        if (userSession == null) {
            return "redirect:/login";
        }

        model.addAttribute("userName", userSession.getName());
        model.addAttribute("userType", userSession.getUserType());
        model.addAttribute("userSession", userSession);
        return "database-management";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
    // =====================================================
// AGREGAR ESTE MÉTODO EN TU ARCHIVO Login.java
// =====================================================

    @GetMapping("/tickets")
    public String tickets(HttpSession session, Model model) {
        UserSession userSession = (UserSession) session.getAttribute("userSession");

        if (userSession == null) {
            return "redirect:/login";
        }

        model.addAttribute("userName", userSession.getName());
        model.addAttribute("userType", userSession.getUserType());
        model.addAttribute("userSession", userSession);
        return "tickets";
    }
}