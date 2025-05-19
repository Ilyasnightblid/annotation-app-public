package com.example.annotationapp.repository;

import com.example.annotationapp.entity.Dataset;
import com.example.annotationapp.entity.TextPair;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TextPairRepository extends JpaRepository<TextPair, Long> {
    List<TextPair> findByDataset(Dataset dataset);

    // Requête pour trouver les TextPairs d'un dataset qui n'ont pas encore d'annotation
    // ou dont l'annotation n'a pas encore d'annotateur assigné
    @Query("SELECT tp FROM TextPair tp WHERE tp.dataset = :dataset AND tp.id NOT IN (SELECT a.textPair.id FROM Annotation a WHERE a.dataset = :dataset AND a.annotator IS NOT NULL)")
    List<TextPair> findUnassignedTextPairsByDataset(Dataset dataset);
}