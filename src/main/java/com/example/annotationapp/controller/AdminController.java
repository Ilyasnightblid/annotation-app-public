package com.example.annotationapp.controller;

import com.example.annotationapp.dto.DatasetCreateDto;
import com.example.annotationapp.dto.export.AnnotationExportDto; // Pour export JSON
import com.example.annotationapp.entity.*;
import com.example.annotationapp.repository.AnnotationRepository;
import com.example.annotationapp.repository.RoleRepository;
import com.example.annotationapp.repository.TextPairRepository;
import com.example.annotationapp.repository.UserRepository;
import com.example.annotationapp.service.AnnotationService;
import com.example.annotationapp.service.DatasetService;
import com.example.annotationapp.util.PasswordGeneratorUtil;
import com.fasterxml.jackson.databind.ObjectMapper; // Pour export JSON
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    private final DatasetService datasetService;
    private final AnnotationService annotationService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TextPairRepository textPairRepository;
    private final AnnotationRepository annotationRepository;
    private final ObjectMapper objectMapper; // Pour l'export JSON

    @Autowired
    public AdminController(DatasetService datasetService,
                           AnnotationService annotationService,
                           UserRepository userRepository,
                           RoleRepository roleRepository,
                           PasswordEncoder passwordEncoder,
                           TextPairRepository textPairRepository,
                           AnnotationRepository annotationRepository,
                           ObjectMapper objectMapper) { // Injection de ObjectMapper
        this.datasetService = datasetService;
        this.annotationService = annotationService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.textPairRepository = textPairRepository;
        this.annotationRepository = annotationRepository;
        this.objectMapper = objectMapper; // Initialisation
    }

    @GetMapping("/dashboard")
    public String adminDashboard(Model model) {
        long datasetCount = datasetService.getAllDatasets().size();
        model.addAttribute("datasetCount", datasetCount);

        Role annotatorRole = roleRepository.findByName("ROLE_ANNOTATOR");
        long annotatorCount = (annotatorRole != null) ? userRepository.findByRolesContaining(annotatorRole).size() : 0;
        model.addAttribute("annotatorCount", annotatorCount);

        long totalTextPairs = textPairRepository.count();
        model.addAttribute("totalTextPairs", totalTextPairs);

        long totalCompletedAnnotations = annotationRepository.countByChosenClassIsNotNull();
        model.addAttribute("totalCompletedAnnotations", totalCompletedAnnotations);

        List<Dataset> allDatasets = datasetService.getAllDatasets();
        Map<String, Double> datasetProgressData = new LinkedHashMap<>();
        if (!allDatasets.isEmpty()) {
            allDatasets.forEach(ds -> {
                long totalPairsInDataset = datasetService.getTotalTextPairs(ds);
                if (totalPairsInDataset > 0) {
                    long annotatedPairsInDataset = datasetService.countAnnotatedTextPairs(ds);
                    double percentage = ((double) annotatedPairsInDataset / totalPairsInDataset) * 100;
                    datasetProgressData.put(ds.getName(), Math.round(percentage * 100.0) / 100.0);
                } else {
                    datasetProgressData.put(ds.getName(), 0.0);
                }
            });
        }
        if (datasetProgressData.isEmpty() && allDatasets.isEmpty()){ // Si pas de datasets du tout
            datasetProgressData.put("No Datasets Yet", 0.0);
        } else if (datasetProgressData.isEmpty()) { // Si datasets existent mais pas de paires / progrès
            allDatasets.stream().limit(3).forEach(ds -> datasetProgressData.put(ds.getName(), 0.0));
            if(datasetProgressData.isEmpty()) { // Fallback ultime si même ça ne marche pas
                datasetProgressData.put("Sample A", 0.0);
            }
        }


        model.addAttribute("datasetNames", datasetProgressData.keySet().stream().collect(Collectors.toList()));
        model.addAttribute("datasetProgressValues", datasetProgressData.values().stream().collect(Collectors.toList()));

        return "admin/dashboard_admin";
    }

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
            result.rejectValue("csvFile", "NotEmpty", "Dataset file is required.");
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
            redirectAttributes.addFlashAttribute("successMessage", "Dataset '" + dto.getName() + "' created successfully!");
        } catch (IllegalArgumentException e) {
            logger.warn("Validation error or business rule violation during dataset creation: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/datasets/new"; // Revenir au formulaire avec le DTO actuel serait mieux pour pré-remplir
        }
        catch (Exception e) {
            logger.error("Error creating dataset {}", dto.getName(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "An unexpected error occurred while creating the dataset. Please check the logs.");
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
            redirectAttributes.addFlashAttribute("successMessage", "Annotators assigned successfully to dataset!");
        } catch (Exception e) {
            logger.error("Error assigning annotators to datasetId: {}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error assigning annotators: " + e.getMessage());
        }
        return "redirect:/admin/datasets/" + id;
    }

    @GetMapping("/annotators")
    public String listAnnotators(Model model) {
        Role annotatorRole = roleRepository.findByName("ROLE_ANNOTATOR");
        if (annotatorRole == null) {
            model.addAttribute("errorMessage", "Annotator role not found. Please initialize roles.");
            model.addAttribute("annotators", List.of());
        } else {
            List<User> annotators = userRepository.findByRolesContaining(annotatorRole)
                    .stream()
                    .filter(user -> !"admin".equals(user.getUsername())) // Plus sûr que user.getUsername().equals("admin") si username peut être null
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
    public String addAnnotator(@ModelAttribute("user") User userFormData,
                               BindingResult result,
                               RedirectAttributes redirectAttributes, Model model) { // Ajout de Model pour renvoyer l'objet en cas d'erreur

        if (userFormData.getUsername() == null || userFormData.getUsername().trim().isEmpty()) {
            result.rejectValue("username", "NotEmpty", "Username is required");
        } else if (userRepository.findByUsername(userFormData.getUsername().trim()).isPresent()) {
            result.rejectValue("username", "Duplicate", "Username already exists");
        }
        if (userFormData.getPrenom() == null || userFormData.getPrenom().trim().isEmpty()) {
            result.rejectValue("prenom", "NotEmpty", "First name is required");
        }
        if (userFormData.getNom() == null || userFormData.getNom().trim().isEmpty()) {
            result.rejectValue("nom", "NotEmpty", "Last name is required");
        }

        if (result.hasErrors()) {
            // L'objet 'userFormData' (qui est lié à "user" dans le modèle) contient déjà les erreurs
            // et sera retourné au template
            return "admin/add_annotator";
        }

        try {
            Role annotatorRole = roleRepository.findByName("ROLE_ANNOTATOR");
            if (annotatorRole == null) {
                logger.warn("ROLE_ANNOTATOR not found, creating it now.");
                annotatorRole = roleRepository.save(new Role("ROLE_ANNOTATOR"));
            }

            User newUser = new User();
            newUser.setUsername(userFormData.getUsername().trim());
            newUser.setNom(userFormData.getNom().trim());
            newUser.setPrenom(userFormData.getPrenom().trim());
            newUser.setRoles(Set.of(annotatorRole));

            String generatedPassword = PasswordGeneratorUtil.generateDefaultPassword();
            newUser.setPassword(passwordEncoder.encode(generatedPassword));

            userRepository.save(newUser);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Annotator '" + newUser.getUsername() + "' added successfully. Password has been auto-generated.");

            logger.info(">>>> [DEV ONLY - REMOVE IN PROD] Annotator created: User = {}, Generated Password (plain) = {}",
                    newUser.getUsername(), generatedPassword);

        } catch (Exception e) {
            logger.error("Error adding annotator: {}", userFormData.getUsername(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error adding annotator: " + e.getMessage());
            // Si une erreur non liée à la validation se produit, on revient au formulaire
            // Il est bon de remettre l'objet 'user' dans le modèle pour que les champs soient pré-remplis
            model.addAttribute("user", userFormData);
            return "admin/add_annotator";
        }
        return "redirect:/admin/annotators";
    }

    // --- NOUVEAUX ENDPOINTS POUR L'EXPORT ---
    @GetMapping("/datasets/{id}/export/csv")
    public void exportDatasetAnnotationsCsv(@PathVariable Long id, HttpServletResponse response) {
        Dataset dataset = datasetService.getDatasetById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid dataset Id for CSV export:" + id));

        response.setContentType("text/csv");
        // Rendre le nom de fichier plus robuste
        String safeDatasetName = dataset.getName().replaceAll("[^a-zA-Z0-9._-]", "_");
        response.setHeader("Content-Disposition", "attachment; filename=\"dataset_" + safeDatasetName + "_annotations.csv\"");

        try {
            annotationService.exportAnnotationsToCsv(id, response.getWriter());
        } catch (IOException e) {
            logger.error("Error exporting dataset {} to CSV", id, e);
            // Gérer l'erreur : difficile d'envoyer un message flash car la réponse est déjà engagée
            // On pourrait logger et l'utilisateur verra un échec de téléchargement.
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/datasets/{id}/export/json")
    public void exportDatasetAnnotationsJson(@PathVariable Long id, HttpServletResponse response) {
        Dataset dataset = datasetService.getDatasetById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid dataset Id for JSON export:" + id));

        List<AnnotationExportDto> exportData = annotationService.getAnnotationsForExport(id);

        response.setContentType("application/json");
        String safeDatasetName = dataset.getName().replaceAll("[^a-zA-Z0-9._-]", "_");
        response.setHeader("Content-Disposition", "attachment; filename=\"dataset_" + safeDatasetName + "_annotations.json\"");

        try {
            // Utilisation de l'objectMapper injecté
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(response.getWriter(), exportData);
        } catch (IOException e) {
            logger.error("Error exporting dataset {} to JSON", id, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    // --- Squelettes pour Edit/Delete Annotator (si tu les implémentes plus tard) ---
    @GetMapping("/annotators/edit/{id}")
    public String showEditAnnotatorForm(@PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null || "admin".equals(user.getUsername()) || !user.getRoles().stream().anyMatch(role -> "ROLE_ANNOTATOR".equals(role.getName()))) {
            redirectAttributes.addFlashAttribute("errorMessage", "Annotator not found or action not allowed.");
            return "redirect:/admin/annotators";
        }
        user.setPassword("");
        model.addAttribute("user", user);
        return "admin/edit_annotator";
    }

    @PostMapping("/annotators/update/{id}")
    public String updateAnnotator(@PathVariable("id") Long id,
                                  @ModelAttribute("user") User userForm,
                                  BindingResult result,
                                  RedirectAttributes redirectAttributes, Model model) {
        User existingUser = userRepository.findById(id).orElse(null);
        if (existingUser == null || "admin".equals(existingUser.getUsername())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Annotator not found or action not allowed.");
            return "redirect:/admin/annotators";
        }
        // ... (logique de validation complète ici) ...
        if (result.hasErrors()) {
            userForm.setId(id);
            return "admin/edit_annotator";
        }
        try {
            // ... (logique de mise à jour) ...
            userRepository.save(existingUser);
            redirectAttributes.addFlashAttribute("successMessage", "Annotator updated successfully!");
        } catch (Exception e) {
            // ... (gestion d'erreur) ...
            return "admin/edit_annotator";
        }
        return "redirect:/admin/annotators";
    }

    @GetMapping("/annotators/delete/{id}")
    public String deleteAnnotator(@PathVariable("id") Long id, RedirectAttributes redirectAttributes, @AuthenticationPrincipal UserDetails currentUserDetails) {
        User userToDelete = userRepository.findById(id).orElse(null);
        if (userToDelete == null || "admin".equals(userToDelete.getUsername()) ||
                !userToDelete.getRoles().stream().anyMatch(role -> "ROLE_ANNOTATOR".equals(role.getName())) ||
                (currentUserDetails != null && currentUserDetails.getUsername().equals(userToDelete.getUsername()))) {
            redirectAttributes.addFlashAttribute("errorMessage", "Annotator not found or action not allowed.");
            return "redirect:/admin/annotators";
        }
        try {
            // ... (logique de dé-assignation avant suppression) ...
            userRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("successMessage", "Annotator deleted successfully!");
        } catch(DataIntegrityViolationException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Cannot delete annotator. They may have completed annotations.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting annotator: " + e.getMessage());
        }
        return "redirect:/admin/annotators";
    }
}