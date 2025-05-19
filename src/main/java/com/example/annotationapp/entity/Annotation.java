package com.example.annotationapp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "annotations")
@Getter
@Setter
@NoArgsConstructor
public class Annotation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "text_pair_id", nullable = false)
    private TextPair textPair;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "annotator_id") // Peut être null si pas encore assigné ou dé-assigné
    private User annotator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false) // Pour faciliter les requêtes par dataset
    private Dataset dataset;

    private String chosenClass; // La classe choisie par l'annotateur

    // Constructeur pour une tâche assignée mais pas encore annotée
    public Annotation(TextPair textPair, User annotator, Dataset dataset) {
        this.textPair = textPair;
        this.annotator = annotator;
        this.dataset = dataset;
    }
}
