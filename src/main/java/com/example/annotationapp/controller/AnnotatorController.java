package com.example.annotationapp.controller;

import com.example.annotationapp.dto.PasswordChangeDto; // Ton DTO pour le changement de mdp
import com.example.annotationapp.entity.Annotation;
import com.example.annotationapp.entity.User;
import com.example.annotationapp.repository.UserRepository;
import com.example.annotationapp.service.AnnotationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid; // Pour @Valid
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/annotator")
public class AnnotatorController {

    private final AnnotationService annotationService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public AnnotatorController(AnnotationService annotationService,
                               UserRepository userRepository,
                               PasswordEncoder passwordEncoder) {
        this.annotationService = annotationService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    private User getCurrentUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new RuntimeException("User details are null, cannot identify current user.");
        }
        return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found in database: " + userDetails.getUsername()));
    }

    @GetMapping("/dashboard")
    public String annotatorDashboard(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);

        // Statistiques
        long totalCompleted = annotationService.getTotalAnnotationsCompletedByUser(currentUser);
        long pendingTasksCount = annotationService.getPendingTasksCountForUser(currentUser); // Utilise la nouvelle méthode
        Map<String, Long> classDistribution = annotationService.getClassDistributionForAnnotator(currentUser);

        model.addAttribute("annotatorName", currentUser.getPrenom() + " " + currentUser.getNom());
        model.addAttribute("totalCompleted", totalCompleted);
        model.addAttribute("pendingTasksCount", pendingTasksCount);

        // Préparer les données pour Chart.js (diagramme circulaire)
        if (classDistribution != null && !classDistribution.isEmpty()) {
            model.addAttribute("classLabels", new ArrayList<>(classDistribution.keySet()));
            model.addAttribute("classCounts", new ArrayList<>(classDistribution.values()));
        } else {
            // Fournir des listes vides pour éviter les erreurs Thymeleaf si aucune donnée
            model.addAttribute("classLabels", new ArrayList<String>());
            model.addAttribute("classCounts", new ArrayList<Long>());
        }

        // Liste des tâches en attente
        List<Annotation> tasks = annotationService.getPendingTasksForAnnotator(currentUser);
        model.addAttribute("tasks", tasks);

        return "annotator/dashboard_annotator";
    }

    @GetMapping("/tasks/{annotationId}/annotate")
    public String showAnnotationTaskForm(@PathVariable Long annotationId, Model model, @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        Annotation annotation = annotationService.getAnnotationById(annotationId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid annotation task Id:" + annotationId));

        if (annotation.getAnnotator() == null || !annotation.getAnnotator().getId().equals(currentUser.getId())) {
            return "redirect:/annotator/dashboard?error=unauthorized";
        }
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
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error saving annotation: " + e.getMessage());
            return "redirect:/annotator/tasks/" + annotationId + "/annotate";
        }
        return "redirect:/annotator/dashboard";
    }

    // Tes méthodes de profil (laissées telles quelles)
    @GetMapping("/profile")
    public String viewProfile(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        model.addAttribute("user", currentUser);
        model.addAttribute("passwordChangeDto", new PasswordChangeDto());
        return "annotator/profile";
    }

    @PostMapping("/profile/change-password")
    public String changePassword(@ModelAttribute("passwordChangeDto") @Valid PasswordChangeDto passwordChangeDto,
                                 BindingResult result,
                                 @AuthenticationPrincipal UserDetails userDetails,
                                 RedirectAttributes redirectAttributes, Model model) {
        User currentUser = getCurrentUser(userDetails);
        model.addAttribute("user", currentUser);

        if (passwordChangeDto.getCurrentPassword() == null || !passwordEncoder.matches(passwordChangeDto.getCurrentPassword(), currentUser.getPassword())) {
            result.rejectValue("currentPassword", "password.mismatch", "Current password is incorrect.");
        }
        if (passwordChangeDto.getNewPassword() == null || passwordChangeDto.getNewPassword().length() < 8) { // Ajuste la longueur minimale si besoin
            result.rejectValue("newPassword", "password.length", "New password must be at least 8 characters long.");
        }
        if (passwordChangeDto.getNewPassword() != null && !passwordChangeDto.getNewPassword().equals(passwordChangeDto.getConfirmNewPassword())) {
            result.rejectValue("confirmNewPassword", "password.confirmation", "New passwords do not match.");
        }

        if (result.hasErrors()) {
            return "annotator/profile";
        }

        currentUser.setPassword(passwordEncoder.encode(passwordChangeDto.getNewPassword()));
        userRepository.save(currentUser);

        redirectAttributes.addFlashAttribute("successMessage", "Password changed successfully!");
        return "redirect:/annotator/profile";
    }
}