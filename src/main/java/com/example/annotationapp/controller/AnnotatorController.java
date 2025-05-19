package com.example.annotationapp.controller;

import com.example.annotationapp.entity.Annotation;
import com.example.annotationapp.entity.User;
import com.example.annotationapp.repository.UserRepository;
import com.example.annotationapp.service.AnnotationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/annotator")
public class AnnotatorController {

    @Autowired
    private AnnotationService annotationService;

    @Autowired
    private UserRepository userRepository;

    private User getCurrentUser(UserDetails userDetails) {
        return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @GetMapping("/dashboard")
    public String annotatorDashboard(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        List<Annotation> tasks = annotationService.getPendingTasksForAnnotator(currentUser);
        model.addAttribute("tasks", tasks);
        model.addAttribute("annotatorName", currentUser.getPrenom() + " " + currentUser.getNom());
        return "annotator/dashboard_annotator";
    }

    @GetMapping("/tasks/{annotationId}/annotate")
    public String showAnnotationTaskForm(@PathVariable Long annotationId, Model model, @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        Annotation annotation = annotationService.getAnnotationById(annotationId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid annotation task Id:" + annotationId));

        // Vérifier que la tâche appartient bien à l'annotateur courant
        if (!annotation.getAnnotator().getId().equals(currentUser.getId())) {
            // Gérer l'accès non autorisé, par ex. rediriger avec un message d'erreur
            return "redirect:/annotator/dashboard?error=unauthorized";
        }
        // Vérifier si la tâche est déjà complétée
        if (annotation.getChosenClass() != null) {
            return "redirect:/annotator/dashboard?error=task_already_completed";
        }

        model.addAttribute("annotationTask", annotation);
        model.addAttribute("dataset", annotation.getDataset());
        model.addAttribute("textPair", annotation.getTextPair());
        model.addAttribute("possibleClasses", annotation.getDataset().getClassesAsList());
        return "annotator/annotate_task";
    }

    @PostMapping("/tasks/{annotationId}/annotate")
    public String submitAnnotation(@PathVariable Long annotationId,
                                   @RequestParam String chosenClass,
                                   @AuthenticationPrincipal UserDetails userDetails,
                                   RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(userDetails);
        try {
            annotationService.saveAnnotationChoice(annotationId, chosenClass, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "Annotation saved successfully!");
        } catch (SecurityException se) {
            redirectAttributes.addFlashAttribute("errorMessage", se.getMessage());
        }
        catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error saving annotation: " + e.getMessage());
            return "redirect:/annotator/tasks/" + annotationId + "/annotate"; // Revenir au formulaire
        }
        return "redirect:/annotator/dashboard";
    }
}