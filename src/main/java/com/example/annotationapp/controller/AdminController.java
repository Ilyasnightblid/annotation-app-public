package com.example.annotationapp.controller;

import com.example.annotationapp.dto.DatasetCreateDto;
import com.example.annotationapp.entity.*;
import com.example.annotationapp.repository.RoleRepository;
import com.example.annotationapp.repository.UserRepository;
import com.example.annotationapp.service.AnnotationService;
import com.example.annotationapp.service.DatasetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import java.util.stream.Collectors; // Pour les données du graphique
import java.util.List; // Pour les données du graphique
import java.util.Map; // Pour les données du graphique
import java.util.LinkedHashMap; // Pour un ordre prévisible dans la map du graphique


@Controller
@RequestMapping("/admin")
public class AdminController {

    private final DatasetService datasetService;
    private final AnnotationService annotationService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public AdminController(DatasetService datasetService,
                           AnnotationService annotationService,
                           UserRepository userRepository,
                           RoleRepository roleRepository,
                           PasswordEncoder passwordEncoder) {
        this.datasetService = datasetService;
        this.annotationService = annotationService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }


    @GetMapping("/dashboard")
    public String adminDashboard(Model model) {
        // 1. Nombre de Datasets
        long datasetCount = datasetService.getAllDatasets().size();
        model.addAttribute("datasetCount", datasetCount);

        // 2. Nombre d’Annotateurs
        Role annotatorRole = roleRepository.findByName("ROLE_ANNOTATOR");
        long annotatorCount = userRepository.findByRolesContaining(annotatorRole).size();
        model.addAttribute("annotatorCount", annotatorCount);

        // 3. Annotations Today (Utilise la méthode du service, même si elle est fictive pour l'instant)
        long annotationsToday = annotationService.countAnnotationsMadeToday();
        model.addAttribute("annotationsToday", annotationsToday);

        // 4. Taux de complétion global (Moyenne des taux de complétion de tous les datasets)
        List<Dataset> allDatasets = datasetService.getAllDatasets();
        double totalCompletionRate = 0;
        int datasetsWithPairs = 0;
        if (!allDatasets.isEmpty()) {
            for (Dataset ds : allDatasets) {
                long totalPairs = datasetService.getTotalTextPairs(ds);
                if (totalPairs > 0) {
                    long annotatedPairs = datasetService.countAnnotatedTextPairs(ds);
                    totalCompletionRate += ((double) annotatedPairs / totalPairs) * 100;
                    datasetsWithPairs++;
                }
            }
            if (datasetsWithPairs > 0) {
                totalCompletionRate = totalCompletionRate / datasetsWithPairs;
            }
        }
        model.addAttribute("globalCompletionRate", String.format("%.0f%%", totalCompletionRate)); // %.0f pour entier

        // 5. Données pour le graphique de progression des datasets
        // Pour l'instant, données fictives comme demandé, puis on les remplacera
        Map<String, Double> datasetProgressData = new LinkedHashMap<>(); // LinkedHashMap pour garder l'ordre d'insertion
         // Code pour les données réelles (à décommenter plus tard)
        if (!allDatasets.isEmpty()) {
            allDatasets.stream().limit(5) // Limite à 5 datasets pour l'exemple
                .forEach(ds -> {
                    long totalPairs = datasetService.getTotalTextPairs(ds);
                    if (totalPairs > 0) {
                        long annotatedPairs = datasetService.countAnnotatedTextPairs(ds);
                        double percentage = ((double) annotatedPairs / totalPairs) * 100;
                        datasetProgressData.put(ds.getName(), percentage);
                    } else {
                        datasetProgressData.put(ds.getName(), 0.0);
                    }
                });
        }




        model.addAttribute("datasetNames", datasetProgressData.keySet().stream().collect(Collectors.toList()));
        model.addAttribute("datasetProgressValues", datasetProgressData.values().stream().collect(Collectors.toList()));

        return "admin/dashboard_admin";
    }

    // --- Gestion des Datasets --- (Aucun changement dans cette section)
    @GetMapping("/datasets")
    public String listDatasets(Model model) {
        List<Dataset> datasets = datasetService.getAllDatasets();
        model.addAttribute("datasets", datasets);

        Map<Long, String> progressPercentages = new HashMap<>();
        if (datasets != null) {
            datasets.forEach(ds -> {
                if (ds != null && ds.getId() != null) {
                    long total = datasetService.getTotalTextPairs(ds);
                    long annotated = datasetService.countAnnotatedTextPairs(ds);
                    double percentage = (total == 0) ? 0 : ((double) annotated / total) * 100;
                    progressPercentages.put(ds.getId(), String.format("%.2f%%", percentage));
                }
            });
        }
        model.addAttribute("progressPercentages", progressPercentages);

        return "admin/list_datasets";
    }

    @GetMapping("/datasets/new")
    public String showCreateDatasetForm(Model model) {
        model.addAttribute("datasetCreateDto", new DatasetCreateDto());
        return "admin/create_dataset";
    }

    @PostMapping("/datasets/create")
    public String createDataset(@ModelAttribute("datasetCreateDto") DatasetCreateDto dto,
                                BindingResult result, RedirectAttributes redirectAttributes) {
        if (dto.getCsvFile() == null || dto.getCsvFile().isEmpty()) {
            result.rejectValue("csvFile", "NotEmpty", "CSV file is required.");
        }
        if (dto.getName() == null || dto.getName().trim().isEmpty()){
            result.rejectValue("name", "NotEmpty", "Dataset name is required.");
        }
        if (dto.getPossibleClasses() == null || dto.getPossibleClasses().trim().isEmpty()){
            result.rejectValue("possibleClasses", "NotEmpty", "Possible classes are required.");
        }

        if (result.hasErrors()) {
            return "admin/create_dataset";
        }
        try {
            datasetService.createDataset(dto);
            redirectAttributes.addFlashAttribute("successMessage", "Dataset created successfully!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/datasets/new";
        }
        catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error creating dataset: " + e.getMessage());
            return "redirect:/admin/datasets/new";
        }
        return "redirect:/admin/datasets";
    }

    @GetMapping("/datasets/{id}")
    public String datasetDetails(@PathVariable Long id, Model model) {
        Dataset dataset = datasetService.getDatasetById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid dataset Id:" + id));
        List<TextPair> textPairs = datasetService.getTextPairsByDataset(dataset);
        List<User> assignedAnnotators = annotationService.getAssignedAnnotatorsForDataset(id);
        List<Annotation> annotations = annotationService.getAnnotationsForDataset(id);

        model.addAttribute("dataset", dataset);
        model.addAttribute("textPairs", textPairs);
        model.addAttribute("assignedAnnotators", assignedAnnotators);
        model.addAttribute("annotations", annotations);

        long total = datasetService.getTotalTextPairs(dataset);
        long annotatedCount = datasetService.countAnnotatedTextPairs(dataset);
        double percentage = (total == 0) ? 0 : ((double) annotatedCount / total) * 100;
        model.addAttribute("progressPercentage", String.format("%.2f%%", percentage));

        return "admin/dataset_details";
    }

    @PostMapping("/annotations/{annotationId}/deassign")
    public String deassignAnnotator(@PathVariable Long annotationId,
                                    @RequestParam Long datasetId,
                                    RedirectAttributes redirectAttributes,
                                    @AuthenticationPrincipal UserDetails adminUserDetails) {
        try {
            User adminUser = userRepository.findByUsername(adminUserDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("Admin user not found for de-assignment"));

            annotationService.deassignAnnotatorFromAnnotation(annotationId, adminUser);
            redirectAttributes.addFlashAttribute("successMessage", "Annotator de-assigned from task (if it was pending).");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error de-assigning annotator: " + e.getMessage());
        }
        return "redirect:/admin/datasets/" + datasetId;
    }


    // --- Affectation des Annotateurs --- (Aucun changement dans cette section)
    @GetMapping("/datasets/{id}/assign")
    public String showAssignAnnotatorsForm(@PathVariable Long id, Model model) {
        Dataset dataset = datasetService.getDatasetById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid dataset Id:" + id));
        Role annotatorRole = roleRepository.findByName("ROLE_ANNOTATOR");
        List<User> allAnnotators = userRepository.findByRolesContaining(annotatorRole);

        model.addAttribute("dataset", dataset);
        model.addAttribute("allAnnotators", allAnnotators);
        return "admin/assign_annotators";
    }

    @PostMapping("/datasets/{id}/assign")
    public String assignAnnotators(@PathVariable Long id, @RequestParam(value = "annotatorIds", required = false) List<Long> annotatorIds, RedirectAttributes redirectAttributes) {
        if (annotatorIds == null || annotatorIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "No annotators selected.");
            return "redirect:/admin/datasets/" + id + "/assign";
        }
        try {
            annotationService.assignAnnotatorsToDataset(id, annotatorIds);
            redirectAttributes.addFlashAttribute("successMessage", "Annotators assigned successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error assigning annotators: " + e.getMessage());
        }
        return "redirect:/admin/datasets/" + id;
    }

    // --- Gestion des Annotateurs (Comptes) ---
    @GetMapping("/annotators")
    public String listAnnotators(Model model) {
        Role annotatorRole = roleRepository.findByName("ROLE_ANNOTATOR");
        if (annotatorRole == null) {
            model.addAttribute("errorMessage", "Annotator role not found. Please initialize roles.");
            model.addAttribute("annotators", List.of());
        } else {
            List<User> annotators = userRepository.findByRolesContaining(annotatorRole)
                    .stream()
                    .filter(user -> !user.getUsername().equals("admin")) // Ne pas lister l'admin pour modification/suppression
                    .collect(Collectors.toList());
            model.addAttribute("annotators", annotators);
        }
        return "admin/list_annotators";
    }

    @GetMapping("/annotators/new")
    public String showAddAnnotatorForm(Model model) {
        model.addAttribute("user", new User());
        return "admin/add_annotator";
    }

    @PostMapping("/annotators/add")
    public String addAnnotator(@ModelAttribute("user") User user, BindingResult result, RedirectAttributes redirectAttributes) {
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            result.rejectValue("username", "NotEmpty", "Username is required");
        } else if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            result.rejectValue("username", "Duplicate", "Username already exists");
        }
        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            result.rejectValue("password", "NotEmpty", "Password is required");
        }
        if (user.getPrenom() == null || user.getPrenom().trim().isEmpty()) {
            result.rejectValue("prenom", "NotEmpty", "First name is required");
        }
        if (user.getNom() == null || user.getNom().trim().isEmpty()) {
            result.rejectValue("nom", "NotEmpty", "Last name is required");
        }

        if (result.hasErrors()) {
            return "admin/add_annotator";
        }

        try {
            Role annotatorRole = roleRepository.findByName("ROLE_ANNOTATOR");
            if (annotatorRole == null) {
                annotatorRole = roleRepository.save(new Role("ROLE_ANNOTATOR"));
            }
            user.setRoles(Set.of(annotatorRole));
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            userRepository.save(user);
            redirectAttributes.addFlashAttribute("successMessage", "Annotator added successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error adding annotator: " + e.getMessage());
        }
        return "redirect:/admin/annotators";
    }

    @GetMapping("/annotators/edit/{id}")
    public String showEditAnnotatorForm(@PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
        User user = userRepository.findById(id).orElse(null);

        // Empêcher l'édition de l'utilisateur 'admin' ou si l'utilisateur n'est pas un annotateur
        if (user == null || user.getUsername().equals("admin") || !user.getRoles().stream().anyMatch(role -> role.getName().equals("ROLE_ANNOTATOR"))) {
            redirectAttributes.addFlashAttribute("errorMessage", "Annotator not found or action not allowed.");
            return "redirect:/admin/annotators";
        }

        user.setPassword(""); // Ne pas afficher le hash du mot de passe dans le formulaire
        model.addAttribute("user", user);
        return "admin/edit_annotator";
    }

    @PostMapping("/annotators/update/{id}")
    public String updateAnnotator(@PathVariable("id") Long id,
                                  @ModelAttribute("user") User userForm,
                                  BindingResult result,
                                  RedirectAttributes redirectAttributes) {

        User existingUser = userRepository.findById(id).orElse(null);
        // Empêcher la modification de l'utilisateur 'admin'
        if (existingUser == null || existingUser.getUsername().equals("admin")) {
            redirectAttributes.addFlashAttribute("errorMessage", "Annotator not found or action not allowed.");
            return "redirect:/admin/annotators";
        }

        if (userForm.getUsername() == null || userForm.getUsername().trim().isEmpty()) {
            result.rejectValue("username", "NotEmpty", "Username is required");
        } else {
            User userByUsername = userRepository.findByUsername(userForm.getUsername()).orElse(null);
            if (userByUsername != null && !userByUsername.getId().equals(id)) {
                result.rejectValue("username", "Duplicate", "Username already exists for another user.");
            }
        }
        if (userForm.getPrenom() == null || userForm.getPrenom().trim().isEmpty()) {
            result.rejectValue("prenom", "NotEmpty", "First name is required");
        }
        if (userForm.getNom() == null || userForm.getNom().trim().isEmpty()) {
            result.rejectValue("nom", "NotEmpty", "Last name is required");
        }

        if (result.hasErrors()) {
            userForm.setId(id);
            return "admin/edit_annotator";
        }

        try {
            existingUser.setUsername(userForm.getUsername());
            existingUser.setNom(userForm.getNom());
            existingUser.setPrenom(userForm.getPrenom());

            if (userForm.getPassword() != null && !userForm.getPassword().trim().isEmpty()) {
                existingUser.setPassword(passwordEncoder.encode(userForm.getPassword()));
            }

            userRepository.save(existingUser);
            redirectAttributes.addFlashAttribute("successMessage", "Annotator updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating annotator: " + e.getMessage());
            userForm.setId(id);
            return "admin/edit_annotator";
        }
        return "redirect:/admin/annotators";
    }

    @GetMapping("/annotators/delete/{id}")
    public String deleteAnnotator(@PathVariable("id") Long id, RedirectAttributes redirectAttributes, @AuthenticationPrincipal UserDetails currentUserDetails) {
        User userToDelete = userRepository.findById(id).orElse(null);

        // Empêcher la suppression de l'utilisateur 'admin' ou si l'utilisateur n'est pas un annotateur
        // ou si l'admin essaie de se supprimer lui-même via cette route (même si 'admin' est filtré avant)
        if (userToDelete == null || userToDelete.getUsername().equals("admin") ||
                !userToDelete.getRoles().stream().anyMatch(role -> role.getName().equals("ROLE_ANNOTATOR")) ||
                (currentUserDetails != null && currentUserDetails.getUsername().equals(userToDelete.getUsername()))) {
            redirectAttributes.addFlashAttribute("errorMessage", "Annotator not found or action not allowed.");
            return "redirect:/admin/annotators";
        }

        try {
            // Étape 1: Dé-assigner les tâches PENDANTES de l'annotateur
            annotationService.deassignAllPendingTasksFromAnnotator(userToDelete);

            // Étape 2: Essayer de supprimer l'utilisateur
            // Si l'annotateur a des tâches COMPLÉTÉES, cela lèvera une DataIntegrityViolationException
            // à cause de la contrainte de clé étrangère dans la table 'annotations'.
            userRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("successMessage", "Annotator deleted successfully! Their pending tasks have been de-assigned.");
        } catch(DataIntegrityViolationException e) {
            // Ce message est celui que tu as vu. Il est correct si l'annotateur a des tâches complétées.
            redirectAttributes.addFlashAttribute("errorMessage", "Cannot delete annotator. They might still have COMPLETED annotations referencing them. Pending tasks were de-assigned. To delete this user, their completed annotations must be handled manually or re-assigned.");
        }
        catch (Exception e) { // Autres exceptions possibles
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting annotator: " + e.getMessage());
        }
        return "redirect:/admin/annotators";
    }
}