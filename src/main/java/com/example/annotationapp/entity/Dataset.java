package com.example.annotationapp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "datasets")
@Getter
@Setter
@NoArgsConstructor
public class Dataset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Lob // Pour les textes longs
    private String description;

    @Column(nullable = false)
    private String possibleClasses; // Séparées par ";"

    @OneToMany(mappedBy = "dataset", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TextPair> textPairs = new ArrayList<>();

    @OneToMany(mappedBy = "dataset", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Annotation> annotations = new ArrayList<>();

    public Dataset(String name, String description, String possibleClasses) {
        this.name = name;
        this.description = description;
        this.possibleClasses = possibleClasses;
    }

    public List<String> getClassesAsList() {
        if (possibleClasses == null || possibleClasses.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return List.of(possibleClasses.split(";"));
    }
}
