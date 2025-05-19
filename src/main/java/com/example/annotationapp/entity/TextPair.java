package com.example.annotationapp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "text_pairs")
@Getter
@Setter
@NoArgsConstructor
public class TextPair {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Lob
    @Column(nullable = false)
    private String text1;

    @Lob
    @Column(nullable = false)
    private String text2;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false)
    private Dataset dataset;

    public TextPair(String text1, String text2, Dataset dataset) {
        this.text1 = text1;
        this.text2 = text2;
        this.dataset = dataset;
    }
}