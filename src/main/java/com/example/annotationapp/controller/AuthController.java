package com.example.annotationapp.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthController {

    @GetMapping("/login")
    public String login() {
        return "auth/login"; // Renvoie vers templates/auth/login.html
    }

    @GetMapping("/")
    public String home() {
        // Redirigera vers le dashboard approprié grâce au AuthenticationSuccessHandler
        // Ou on peut mettre une logique ici si on veut une page d'accueil générique avant redirection
        return "redirect:/login"; // Pour l'instant, redirige vers login si on accède à la racine
    }
}