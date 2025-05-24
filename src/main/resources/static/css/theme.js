// src/main/resources/static/js/theme.js

document.addEventListener("DOMContentLoaded", () => {
    // Récupérer le bouton de basculement de thème
    const themeToggle = document.getElementById("themeToggle")

    // Vérifier si un thème est déjà enregistré dans le localStorage
    const savedTheme = localStorage.getItem("theme")

    // Appliquer le thème sauvegardé ou utiliser le thème du système
    if (savedTheme) {
        document.documentElement.classList.add(savedTheme)
    } else {
        // Détecter la préférence du système
        const prefersDarkMode = window.matchMedia("(prefers-color-scheme: dark)").matches
        if (prefersDarkMode) {
            document.documentElement.classList.add("dark-theme")
            localStorage.setItem("theme", "dark-theme")
        } else {
            document.documentElement.classList.add("light-theme")
            localStorage.setItem("theme", "light-theme")
        }
    }

    // Ajouter un écouteur d'événement pour le bouton de basculement
    themeToggle.addEventListener("click", () => {
        if (document.documentElement.classList.contains("dark-theme")) {
            // Passer au thème clair
            document.documentElement.classList.remove("dark-theme")
            document.documentElement.classList.add("light-theme")
            localStorage.setItem("theme", "light-theme")
        } else {
            // Passer au thème sombre
            document.documentElement.classList.remove("light-theme")
            document.documentElement.classList.add("dark-theme")
            localStorage.setItem("theme", "dark-theme")
        }
    })
})
