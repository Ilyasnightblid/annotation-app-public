package com.example.annotationapp.dto.export;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AnnotatorExportDto {
    private String username;
    private String nom;
    private String prenom;
}