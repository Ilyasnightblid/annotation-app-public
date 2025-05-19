package com.example.annotationapp.repository;

import com.example.annotationapp.entity.Annotation;
import com.example.annotationapp.entity.Dataset;
import com.example.annotationapp.entity.TextPair;
import com.example.annotationapp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnnotationRepository extends JpaRepository<Annotation, Long> {
    List<Annotation> findByDataset(Dataset dataset);
    List<Annotation> findByAnnotator(User annotator);
    List<Annotation> findByAnnotatorAndDataset(User annotator, Dataset dataset);
    // Pour trouver les tâches en attente pour un annotateur sur un dataset spécifique
    List<Annotation> findByAnnotatorAndDatasetAndChosenClassIsNull(User annotator, Dataset dataset);
    // Pour trouver toutes les tâches en attente pour un annotateur
    List<Annotation> findByAnnotatorAndChosenClassIsNull(User annotator);
    Optional<Annotation> findByTextPairAndDataset(TextPair textPair, Dataset dataset);
    boolean existsByTextPairAndDatasetAndAnnotatorIsNotNull(TextPair textPair, Dataset dataset);
}