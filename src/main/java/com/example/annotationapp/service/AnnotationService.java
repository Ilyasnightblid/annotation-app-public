package com.example.annotationapp.service;

import com.example.annotationapp.dto.export.AnnotationExportDto;
import com.example.annotationapp.dto.export.AnnotatorExportDto;
import com.example.annotationapp.entity.Annotation;
import com.example.annotationapp.entity.Dataset;
import com.example.annotationapp.entity.TextPair;
import com.example.annotationapp.entity.User;
import com.example.annotationapp.repository.AnnotationRepository;
import com.example.annotationapp.repository.DatasetRepository;
import com.example.annotationapp.repository.TextPairRepository;
import com.example.annotationapp.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AnnotationService {

    private static final Logger logger = LoggerFactory.getLogger(AnnotationService.class);

    private final AnnotationRepository annotationRepository;
    private final DatasetRepository datasetRepository;
    private final UserRepository userRepository;
    private final TextPairRepository textPairRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public AnnotationService(AnnotationRepository annotationRepository,
                             DatasetRepository datasetRepository,
                             UserRepository userRepository,
                             TextPairRepository textPairRepository,
                             ObjectMapper objectMapper) {
        this.annotationRepository = annotationRepository;
        this.datasetRepository = datasetRepository;
        this.userRepository = userRepository;
        this.textPairRepository = textPairRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void assignAnnotatorsToDataset(Long datasetId, List<Long> annotatorIds) {
        logger.info("Assigning annotators {} to datasetId {}", annotatorIds, datasetId);
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> {
                    logger.error("Dataset not found with ID: {}", datasetId);
                    return new IllegalArgumentException("Dataset not found with ID: " + datasetId);
                });

        List<User> annotators = userRepository.findAllById(annotatorIds);
        if (annotators.isEmpty()) {
            logger.warn("No annotators selected or found for IDs: {}", annotatorIds);
            throw new IllegalArgumentException("No annotators selected or found");
        }

        List<TextPair> allTextPairs = textPairRepository.findByDataset(dataset);
        if (allTextPairs.isEmpty()) {
            logger.info("No text pairs in dataset {} to assign.", datasetId);
            return;
        }

        List<TextPair> unassignedTextPairs = new ArrayList<>();
        for (TextPair tp : allTextPairs) {
            if (!annotationRepository.existsByTextPairAndDatasetAndAnnotatorIsNotNull(tp, dataset)) {
                unassignedTextPairs.add(tp);
            }
        }

        if (unassignedTextPairs.isEmpty()) {
            logger.info("No unassigned text pairs to distribute in dataset {}.", datasetId);
            return;
        }

        logger.info("Distributing {} unassigned text pairs among {} annotators for dataset {}.",
                unassignedTextPairs.size(), annotators.size(), datasetId);

        int annotatorIndex = 0;
        List<Annotation> annotationsToSave = new ArrayList<>();
        for (TextPair textPair : unassignedTextPairs) {
            User currentAnnotator = annotators.get(annotatorIndex % annotators.size());
            Optional<Annotation> existingAnnotationOpt = annotationRepository.findByTextPairAndDataset(textPair, dataset);
            if (existingAnnotationOpt.isPresent()) {
                Annotation existingAnnotation = existingAnnotationOpt.get();
                if (existingAnnotation.getAnnotator() == null) {
                    existingAnnotation.setAnnotator(currentAnnotator);
                    annotationsToSave.add(existingAnnotation);
                }
            } else {
                Annotation newAnnotation = new Annotation(textPair, currentAnnotator, dataset);
                annotationsToSave.add(newAnnotation);
            }
            annotatorIndex++;
        }
        if (!annotationsToSave.isEmpty()) {
            annotationRepository.saveAll(annotationsToSave);
            logger.info("Saved {} new/updated assignments for dataset {}.", annotationsToSave.size(), datasetId);
        }
    }

    public List<Annotation> getPendingTasksForAnnotator(User annotator) {
        return annotationRepository.findByAnnotatorAndChosenClassIsNull(annotator);
    }

    public Optional<Annotation> getAnnotationById(Long id) {
        return annotationRepository.findById(id);
    }

    @Transactional
    public Annotation saveAnnotationChoice(Long annotationId, String chosenClass, User annotator) {
        Annotation annotation = annotationRepository.findById(annotationId)
                .orElseThrow(() -> new IllegalArgumentException("Annotation task not found: " + annotationId));

        if (annotation.getAnnotator() == null || !annotation.getAnnotator().getId().equals(annotator.getId())) {
            logger.warn("User {} attempted to annotate task {} not assigned to them.", annotator.getUsername(), annotationId);
            throw new SecurityException("User not authorized to annotate this task or task not assigned.");
        }
        if (annotation.getDataset() == null || annotation.getDataset().getPossibleClasses() == null ||
                !annotation.getDataset().getClassesAsList().contains(chosenClass)) {
            String possibleClassesStr = (annotation.getDataset() != null && annotation.getDataset().getPossibleClasses() != null) ? annotation.getDataset().getPossibleClasses() : "N/A";
            logger.warn("Invalid class '{}' chosen for dataset. Possible classes: {}", chosenClass, possibleClassesStr);
            throw new IllegalArgumentException("Invalid class '" + chosenClass + "' chosen for this dataset.");
        }

        annotation.setChosenClass(chosenClass);
        // Logic for updatedAt if you add that field
        Annotation savedAnnotation = annotationRepository.save(annotation);
        logger.info("User {} saved annotation for task {} with class '{}'", annotator.getUsername(), annotationId, chosenClass);
        return savedAnnotation;
    }

    public List<User> getAssignedAnnotatorsForDataset(Long datasetId) {
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found with ID: " + datasetId));
        // Assumes AnnotationRepository has findByDatasetAndAnnotatorIsNotNull
        return annotationRepository.findByDatasetAndAnnotatorIsNotNull(dataset).stream()
                .map(Annotation::getAnnotator)
                .distinct()
                .collect(Collectors.toList());
    }

    public List<Annotation> getAnnotationsForDataset(Long datasetId) {
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found with ID: " + datasetId));
        return annotationRepository.findByDataset(dataset);
    }

    @Transactional
    public void deassignAnnotatorFromAnnotation(Long annotationId, User adminUser) {
        Annotation annotation = annotationRepository.findById(annotationId)
                .orElseThrow(() -> new IllegalArgumentException("Annotation not found with ID: " + annotationId));
        if (annotation.getChosenClass() == null && annotation.getAnnotator() != null) {
            User previousAnnotator = annotation.getAnnotator();
            annotation.setAnnotator(null);
            annotationRepository.save(annotation);
            logger.info("Admin {} de-assigned pending task {} from annotator {}",
                    adminUser.getUsername(), annotationId, previousAnnotator.getUsername());
        } else if (annotation.getChosenClass() != null) {
            logger.warn("Admin {} attempted to de-assign a completed task {}.",
                    adminUser.getUsername(), annotationId);
        }
    }

    @Transactional
    public void deassignAllPendingTasksFromAnnotator(User annotator) {
        List<Annotation> pendingTasks = annotationRepository.findByAnnotatorAndChosenClassIsNull(annotator);
        if (!pendingTasks.isEmpty()) {
            pendingTasks.forEach(task -> task.setAnnotator(null));
            annotationRepository.saveAll(pendingTasks);
            logger.info("De-assigned {} pending tasks from annotator {}", pendingTasks.size(), annotator.getUsername());
        } else {
            logger.info("No pending tasks found to de-assign for annotator {}", annotator.getUsername());
        }
    }

    public List<Annotation> findByAnnotator(User annotator) {
        return annotationRepository.findByAnnotator(annotator);
    }

    public long countAnnotationsMadeToday() {
        // TODO: Implement real logic with an 'annotatedAt' or 'updatedAt' field in Annotation entity
        long completedCount = annotationRepository.countByChosenClassIsNotNull();
        return completedCount > 3 ? (completedCount / 3) + (long)(Math.random() * 3) : (long)(Math.random() * 5); // Adjusted fictive logic
    }

    public void exportAnnotationsToCsv(Long datasetId, Writer writer) throws IOException {
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found with ID: " + datasetId));
        List<Annotation> annotations = annotationRepository.findByDataset(dataset);
        logger.info("Exporting {} annotations to CSV for dataset: {}", annotations.size(), dataset.getName());

        String[] headers = {"annotation_id", "dataset_name", "text_pair_id", "text1", "text2",
                "chosen_class", "annotator_username", "annotator_nom", "annotator_prenom"};

        try (CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(headers))) {
            for (Annotation ann : annotations) {
                TextPair tp = ann.getTextPair();
                User annotator = ann.getAnnotator();
                csvPrinter.printRecord(
                        ann.getId(),
                        dataset.getName(),
                        tp != null ? tp.getId() : "N/A",
                        tp != null ? tp.getText1() : "N/A",
                        tp != null ? tp.getText2() : "N/A",
                        ann.getChosenClass() != null ? ann.getChosenClass() : "NOT_ANNOTATED",
                        annotator != null ? annotator.getUsername() : "N/A",
                        annotator != null ? annotator.getNom() : "N/A",
                        annotator != null ? annotator.getPrenom() : "N/A"
                );
            }
            writer.flush();
        } catch (IOException e) {
            logger.error("Error writing CSV data for dataset {}: {}", dataset.getName(), e.getMessage(), e);
            throw e;
        }
    }

    public List<AnnotationExportDto> getAnnotationsForExport(Long datasetId) {
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found with ID: " + datasetId));
        List<Annotation> annotations = annotationRepository.findByDataset(dataset);
        logger.info("Preparing {} annotations for JSON export for dataset: {}", annotations.size(), dataset.getName());

        return annotations.stream().map(ann -> {
            AnnotatorExportDto annotatorDto = null;
            if (ann.getAnnotator() != null) {
                annotatorDto = new AnnotatorExportDto(
                        ann.getAnnotator().getUsername(),
                        ann.getAnnotator().getNom(),
                        ann.getAnnotator().getPrenom()
                );
            }
            TextPair tp = ann.getTextPair();
            return new AnnotationExportDto(
                    ann.getId(),
                    dataset.getName(),
                    tp != null ? tp.getId() : null,
                    tp != null ? tp.getText1() : "N/A",
                    tp != null ? tp.getText2() : "N/A",
                    ann.getChosenClass(),
                    annotatorDto
            );
        }).collect(Collectors.toList());
    }
}