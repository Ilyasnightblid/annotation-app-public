package com.example.annotationapp.service;

import com.example.annotationapp.entity.Annotation;
import com.example.annotationapp.entity.Dataset;
import com.example.annotationapp.entity.TextPair;
import com.example.annotationapp.entity.User;
import com.example.annotationapp.repository.AnnotationRepository;
import com.example.annotationapp.repository.DatasetRepository;
import com.example.annotationapp.repository.TextPairRepository;
import com.example.annotationapp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AnnotationService {

    private final AnnotationRepository annotationRepository;
    private final DatasetRepository datasetRepository;
    private final UserRepository userRepository;
    private final TextPairRepository textPairRepository;

    @Autowired
    public AnnotationService(AnnotationRepository annotationRepository,
                             DatasetRepository datasetRepository,
                             UserRepository userRepository,
                             TextPairRepository textPairRepository) {
        this.annotationRepository = annotationRepository;
        this.datasetRepository = datasetRepository;
        this.userRepository = userRepository;
        this.textPairRepository = textPairRepository;
    }


    @Transactional
    public void assignAnnotatorsToDataset(Long datasetId, List<Long> annotatorIds) {
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found with ID: " + datasetId));
        List<User> annotators = userRepository.findAllById(annotatorIds);
        if (annotators.isEmpty()) {
            throw new IllegalArgumentException("No annotators selected or found");
        }

        List<TextPair> allTextPairs = textPairRepository.findByDataset(dataset);
        List<TextPair> unassignedTextPairs = new ArrayList<>();

        for(TextPair tp : allTextPairs) {
            if (!annotationRepository.existsByTextPairAndDatasetAndAnnotatorIsNotNull(tp, dataset)) {
                unassignedTextPairs.add(tp);
            }
        }

        if (unassignedTextPairs.isEmpty()) {
            return;
        }

        int annotatorIndex = 0;
        for (TextPair textPair : unassignedTextPairs) {
            User currentAnnotator = annotators.get(annotatorIndex % annotators.size());

            Optional<com.example.annotationapp.entity.Annotation> existingAnnotationOpt =
                    annotationRepository.findByTextPairAndDataset(textPair, dataset);

            if (existingAnnotationOpt.isPresent()) {
                com.example.annotationapp.entity.Annotation existingAnnotation = existingAnnotationOpt.get();
                if (existingAnnotation.getAnnotator() == null) {
                    existingAnnotation.setAnnotator(currentAnnotator);
                    annotationRepository.save(existingAnnotation);
                }
            } else {
                Annotation newAnnotation = new Annotation(textPair, currentAnnotator, dataset);
                annotationRepository.save(newAnnotation);
            }
            annotatorIndex++;
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
            throw new SecurityException("User not authorized to annotate this task or task not assigned.");
        }
        if (!annotation.getDataset().getClassesAsList().contains(chosenClass)) {
            throw new IllegalArgumentException("Invalid class '" + chosenClass + "' chosen for this dataset. Possible classes are: " + annotation.getDataset().getPossibleClasses());
        }

        annotation.setChosenClass(chosenClass);
        return annotationRepository.save(annotation);
    }

    public List<User> getAssignedAnnotatorsForDataset(Long datasetId) {
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found with ID: " + datasetId));

        return annotationRepository.findByDataset(dataset).stream()
                .filter(a -> a.getAnnotator() != null)
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

        // On ne vérifie pas adminUser ici car la méthode est appelée par l'admin
        // et la sécurité de la route est gérée par Spring Security.
        // Si on voulait plus de granularité, on pourrait ajouter une vérification ici.

        if (annotation.getChosenClass() == null && annotation.getAnnotator() != null) {
            annotation.setAnnotator(null);
            annotationRepository.save(annotation);
        } else if (annotation.getChosenClass() != null) {
            // Pour une tâche complétée, on ne fait rien ici car la "désassignation"
            // d'une tâche complétée signifierait perdre l'info de qui l'a faite.
            // Le message d'erreur que tu avais vu était pour la suppression de l'utilisateur, pas cette méthode.
            System.out.println("Annotation " + annotationId + " is already completed. De-assigning annotator from a completed task is not standard procedure here.");
        }
    }

    /**
     * Dé-assigne toutes les tâches PENDANTES d'un annotateur spécifique.
     * Cela met leur champ 'annotator' à null.
     * @param annotator L'utilisateur annotateur dont les tâches pendantes doivent être dé-assignées.
     */
    @Transactional
    public void deassignAllPendingTasksFromAnnotator(User annotator) {
        List<Annotation> pendingTasks = annotationRepository.findByAnnotatorAndChosenClassIsNull(annotator);
        for (Annotation task : pendingTasks) {
            task.setAnnotator(null); // Met l'annotateur à null pour cette tâche
            annotationRepository.save(task); // Sauvegarde le changement
        }
    }

    /**
     * Trouve toutes les annotations (pendantes ou complétées) pour un annotateur spécifique.
     * @param annotator L'utilisateur annotateur.
     * @return Une liste de ses annotations.
     */
    public List<Annotation> findByAnnotator(User annotator) {
        return annotationRepository.findByAnnotator(annotator);
    }
}