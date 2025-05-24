package com.example.annotationapp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
@Entity
@Table(name = "annotations")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class) // IMPORTANT: Active l'audit JPA pour cette entité
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
    @UpdateTimestamp // Se met à jour automatiquement à chaque modification de l'entité
    private LocalDateTime updatedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corrected_by_admin_id")
    private User correctedByAdmin; // L'admin qui a fait la correction

    private LocalDateTime correctedAt; // Quand la correction a eu lieu

// N'oublie pas les getters et setters pour ces nouveaux champs
}
