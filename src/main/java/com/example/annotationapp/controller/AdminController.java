package com.example.annotationapp.controller;

import com.example.annotationapp.dto.DatasetCreateDto;
import com.example.annotationapp.entity.*;
import com.example.annotationapp.repository.RoleRepository;
import com.example.annotationapp.repository.UserRepository;
import com.example.annotationapp.service.AnnotationService;
import com.example.annotationapp.service.DatasetService;
import com.example.annotationapp.util.PasswordGeneratorUtil; // <<< NOUVEL IMPORT
import org.slf4j.Logger;                                 // <<< NOUVEL IMPORT
import org.slf4j.LoggerFactory;                          // <<< NOUVEL IMPORT
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.example.annotationapp.repository.AnnotationRepository; // <<< AJOUTER CET IMPORT
import com.example.annotationapp.repository.TextPairRepository; // <<< AJOUTER CET IMPORT



@Controller
@RequestMapping("/admin")
public class AdminController {

    // Logger pour la classe
    private static final Logger logger = LoggerFactory.getLogger(AdminController.class); // <<< AJOUT DU LOGGER

    private final DatasetService datasetService;
    private final AnnotationService annotationService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TextPairRepository textPairRepository; // <<< INJECTER CE REPOSITORY
    private final AnnotationRepository annotationRepository; // <<< INJECTER CE REPOSITORY

    @Autowired
    public AdminController(DatasetService datasetService,
                           AnnotationService annotationService,
                           UserRepository userRepository,
                           RoleRepository roleRepository,
                           PasswordEncoder passwordEncoder,
                           TextPairRepository textPairRepository,      // <<< AJOUTER
                           AnnotationRepository annotationRepository) {
        this.datasetService = datasetService;
        this.annotationService = annotationService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.textPairRepository = textPairRepository;          // <<< AJOUTER
        this.annotationRepository = annotationRepository;
    }

    @GetMapping("/dashboard")
    public String adminDashboard(Model model) {
        // 1. Nombre de Datasets
        long datasetCount = datasetService.getAllDatasets().size();
        model.addAttribute("datasetCount", datasetCount);

        // 2. Nombre d’Annotateurs
        Role annotatorRole = roleRepository.findByName("ROLE_ANNOTATOR");
        long annotatorCount = 0;
        if (annotatorRole != null) {
            annotatorCount = userRepository.findByRolesContaining(annotatorRole).size();
        }
        model.addAttribute("annotatorCount", annotatorCount);

        // 3. Nombre Total de Tâches (TextPairs) dans tous les datasets
        long totalTextPairs = textPairRepository.count(); // Simple count de toutes les entrées
        model.addAttribute("totalTextPairs", totalTextPairs);

        // 4. Nombre Total d'Annotations Effectuées
        long totalCompletedAnnotations = annotationRepository.countByChosenClassIsNotNull(); // Nécessite une méthode dans le repo
        model.addAttribute("totalCompletedAnnotations", totalCompletedAnnotations);


        // 5. Données pour le graphique de progression des datasets
        List<Dataset> allDatasets = datasetService.getAllDatasets();
        Map<String, Double> datasetProgressData = new LinkedHashMap<>();
        if (!allDatasets.isEmpty()) {
            allDatasets.stream()
                    // .limit(5) // Tu peux garder ou enlever la limite
                    .forEach(ds -> {
                        long totalPairsInDataset = datasetService.getTotalTextPairs(ds);
                        if (totalPairsInDataset > 0) {
                            long annotatedPairsInDataset = datasetService.countAnnotatedTextPairs(ds);
                            double percentage = ((double) annotatedPairsInDataset / totalPairsInDataset) * 100;
                            datasetProgressData.put(ds.getName(), Math.round(percentage * 100.0) / 100.0); // Arrondi à 2 décimales
                        } else {
                            datasetProgressData.put(ds.getName(), 0.0);
                        }
                    });
        }
        // Si aucune donnée de progression réelle, affiche des données exemples pour le graphique
        if (datasetProgressData.isEmpty()){
            datasetProgressData.put("Sample A", 75.0);
            datasetProgressData.put("Sample B", 60.0);
            datasetProgressData.put("Sample C", 30.0);
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
            logger.error("Error creating dataset", e);
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
            logger.error("Error de-assigning annotator for annotationId: {}", annotationId, e);
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
        List<User> allAnnotators = List.of();
        if(annotatorRole != null) {
            allAnnotators = userRepository.findByRolesContaining(annotatorRole);
        }
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
            logger.error("Error assigning annotators to datasetId: {}", id, e);
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
                    .filter(user -> !user.getUsername().equals("admin"))
                    .collect(Collectors.toList());
            model.addAttribute("annotators", annotators);
        }
        return "admin/list_annotators";
    }

    @GetMapping("/annotators/new")
    public String showAddAnnotatorForm(Model model) {
        model.addAttribute("user", new User()); // L'objet User est pour le formulaire
        return "admin/add_annotator";
    }

    // ==================================================
    // MODIFICATIONS POUR LA GÉNÉRATION DE MOT DE PASSE
    // ==================================================
    @PostMapping("/annotators/add")
    public String addAnnotator(@ModelAttribute("user") User userFormData, // Renommé pour clarté
                               BindingResult result,
                               RedirectAttributes redirectAttributes) {

        // Validation des champs reçus du formulaire (username, nom, prenom)
        if (userFormData.getUsername() == null || userFormData.getUsername().trim().isEmpty()) {
            result.rejectValue("username", "NotEmpty", "Username is required");
        } else if (userRepository.findByUsername(userFormData.getUsername()).isPresent()) {
            result.rejectValue("username", "Duplicate", "Username already exists");
        }
        if (userFormData.getPrenom() == null || userFormData.getPrenom().trim().isEmpty()) {
            result.rejectValue("prenom", "NotEmpty", "First name is required");
        }
        if (userFormData.getNom() == null || userFormData.getNom().trim().isEmpty()) {
            result.rejectValue("nom", "NotEmpty", "Last name is required");
        }

        if (result.hasErrors()) {
            // 'userFormData' (avec les erreurs) sera retourné au formulaire
            return "admin/add_annotator";
        }

        try {
            Role annotatorRole = roleRepository.findByName("ROLE_ANNOTATOR");
            if (annotatorRole == null) {
                // Cela ne devrait pas arriver si DataInitializer a bien fonctionné, mais sécurité
                logger.warn("ROLE_ANNOTATOR not found, creating it now.");
                annotatorRole = roleRepository.save(new Role("ROLE_ANNOTATOR"));
            }

            // Créer une nouvelle instance User à sauvegarder
            User newUser = new User();
            newUser.setUsername(userFormData.getUsername().trim());
            newUser.setNom(userFormData.getNom().trim());
            newUser.setPrenom(userFormData.getPrenom().trim());
            newUser.setRoles(Set.of(annotatorRole));

            // Génération et hashage du mot de passe
            String generatedPassword = PasswordGeneratorUtil.generateDefaultPassword();
            newUser.setPassword(passwordEncoder.encode(generatedPassword));

            userRepository.save(newUser);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Annotator '" + newUser.getUsername() + "' added successfully. Password has been auto-generated.");

            // !!! ATTENTION : LOGGING DU MOT DE PASSE EN CLAIR POUR DÉVELOPPEMENT SEULEMENT !!!
            // !!! À SUPPRIMER ABSOLUMENT EN PRODUCTION !!!
            logger.info(">>>> [DEV ONLY - REMOVE IN PROD] Annotator created: User = {}, Generated Password (plain) = {}",
                    newUser.getUsername(), generatedPassword);

        } catch (Exception e) {
            logger.error("Error adding annotator: {}", userFormData.getUsername(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error adding annotator: " + e.getMessage());
            // Il pourrait être utile de renvoyer userFormData au formulaire avec un message d'erreur global
            // model.addAttribute("user", userFormData); // Nécessiterait d'ajouter Model model en paramètre
            return "admin/add_annotator"; // Revenir au formulaire en cas d'erreur non gérée
        }
        return "redirect:/admin/annotators";
    }
    // ==================================================
    // FIN DES MODIFICATIONS POUR LA GÉNÉRATION DE MOT DE PASSE
    // ==================================================


    // Les méthodes showEditAnnotatorForm, updateAnnotator, deleteAnnotator restent inchangées
    // par rapport à la version que tu avais déjà, si elles existent.
    // Si tu ne les as pas encore, ce n'est pas grave pour cette étape.
    // Voici un squelette pour la complétude, mais elles ne sont pas modifiées pour la génération de mot de passe.

    @GetMapping("/annotators/edit/{id}")
    public String showEditAnnotatorForm(@PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
        User user = userRepository.findById(id).orElse(null);

        if (user == null || user.getUsername().equals("admin") || !user.getRoles().stream().anyMatch(role -> role.getName().equals("ROLE_ANNOTATOR"))) {
            redirectAttributes.addFlashAttribute("errorMessage", "Annotator not found or action not allowed.");
            return "redirect:/admin/annotators";
        }

        user.setPassword(""); // Ne pas afficher le hash
        model.addAttribute("user", user);
        return "admin/edit_annotator"; // Tu auras besoin de créer ce template
    }

    @PostMapping("/annotators/update/{id}")
    public String updateAnnotator(@PathVariable("id") Long id,
                                  @ModelAttribute("user") User userForm,
                                  BindingResult result,
                                  RedirectAttributes redirectAttributes, Model model) { // Ajout de Model

        User existingUser = userRepository.findById(id).orElse(null);
        if (existingUser == null || existingUser.getUsername().equals("admin")) {
            redirectAttributes.addFlashAttribute("errorMessage", "Annotator not found or action not allowed.");
            return "redirect:/admin/annotators";
        }

        // Validation de l'username
        if (userForm.getUsername() == null || userForm.getUsername().trim().isEmpty()) {
            result.rejectValue("username", "NotEmpty", "Username is required");
        } else {
            User userByUsername = userRepository.findByUsername(userForm.getUsername().trim()).orElse(null);
            if (userByUsername != null && !userByUsername.getId().equals(id)) {
                result.rejectValue("username", "Duplicate", "Username already exists for another user.");
            }
        }
        // Autres validations (nom, prenom)
        if (userForm.getPrenom() == null || userForm.getPrenom().trim().isEmpty()) {
            result.rejectValue("prenom", "NotEmpty", "First name is required");
        }
        if (userForm.getNom() == null || userForm.getNom().trim().isEmpty()) {
            result.rejectValue("nom", "NotEmpty", "Last name is required");
        }

        if (result.hasErrors()) {
            userForm.setId(id); // Important pour que le formulaire sache quel utilisateur on modifie
            // model.addAttribute("user", userForm); // Déjà fait par @ModelAttribute
            return "admin/edit_annotator";
        }

        try {
            existingUser.setUsername(userForm.getUsername().trim());
            existingUser.setNom(userForm.getNom().trim());
            existingUser.setPrenom(userForm.getPrenom().trim());

            // Mettre à jour le mot de passe SEULEMENT s'il est fourni dans le formulaire
            if (userForm.getPassword() != null && !userForm.getPassword().trim().isEmpty()) {
                existingUser.setPassword(passwordEncoder.encode(userForm.getPassword().trim()));
            }

            userRepository.save(existingUser);
            redirectAttributes.addFlashAttribute("successMessage", "Annotator updated successfully!");
        } catch (Exception e) {
            logger.error("Error updating annotator: {}", existingUser.getUsername(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating annotator: " + e.getMessage());
            userForm.setId(id);
            // model.addAttribute("user", userForm);
            return "admin/edit_annotator";
        }
        return "redirect:/admin/annotators";
    }

    @GetMapping("/annotators/delete/{id}")
    public String deleteAnnotator(@PathVariable("id") Long id, RedirectAttributes redirectAttributes, @AuthenticationPrincipal UserDetails currentUserDetails) {
        User userToDelete = userRepository.findById(id).orElse(null);

        if (userToDelete == null || userToDelete.getUsername().equals("admin") ||
                !userToDelete.getRoles().stream().anyMatch(role -> role.getName().equals("ROLE_ANNOTATOR")) ||
                (currentUserDetails != null && currentUserDetails.getUsername().equals(userToDelete.getUsername()))) {
            redirectAttributes.addFlashAttribute("errorMessage", "Annotator not found or action not allowed.");
            return "redirect:/admin/annotators";
        }

        try {
            // Avant de supprimer un utilisateur, tu pourrais vouloir gérer ses annotations.
            // Option 1: Dé-assigner ses tâches PENDANTES (ne supprime pas les tâches complétées)
            // annotationService.deassignAllPendingTasksFromAnnotator(userToDelete);
            // (Il faudra créer cette méthode dans AnnotationService si tu veux ce comportement)

            // Option 2: Si tu veux supprimer l'utilisateur même s'il a des annotations complétées,
            // il faudrait d'abord supprimer ou anonymiser ses annotations.
            // Par exemple, mettre annotation.setAnnotator(null) pour toutes ses annotations
            // List<Annotation> userAnnotations = annotationRepository.findByAnnotator(userToDelete);
            // userAnnotations.forEach(ann -> ann.setAnnotator(null));
            // annotationRepository.saveAll(userAnnotations);

            // Pour l'instant, on essaie de supprimer. Si des contraintes FK existent, ça échouera.
            userRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("successMessage", "Annotator deleted successfully!");
        } catch(DataIntegrityViolationException e) {
            logger.warn("Cannot delete annotator {} due to existing references (e.g., completed annotations).", userToDelete.getUsername(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Cannot delete annotator. They may have completed annotations. Consider de-assigning or re-assigning their work first.");
        }
        catch (Exception e) {
            logger.error("Error deleting annotator: {}", userToDelete.getUsername(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting annotator: " + e.getMessage());
        }
        return "redirect:/admin/annotators";
    }
}