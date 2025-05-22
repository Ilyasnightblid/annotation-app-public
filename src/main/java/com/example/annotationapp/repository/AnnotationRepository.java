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
    List<Annotation> findByAnnotatorAndChosenClassIsNull(User annotator);
    // List<Annotation> findByAnnotatorAndDatasetAndChosenClassIsNull(User annotator, Dataset dataset); // Tu l'avais peut-être déjà
    Optional<Annotation> findByTextPairAndDataset(TextPair textPair, Dataset dataset);
    boolean existsByTextPairAndDatasetAndAnnotatorIsNotNull(TextPair textPair, Dataset dataset);

    // AJOUTER CETTE MÉTHODE :
    List<Annotation> findByDatasetAndAnnotatorIsNotNull(Dataset dataset);

    // Et la méthode pour compter les annotations complétées pour le dashboard :
    long countByChosenClassIsNotNull();
}