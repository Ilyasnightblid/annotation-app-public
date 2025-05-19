package com.example.annotationapp.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class DatasetCreateDto {
    private String name;
    private String description;
    private String possibleClasses; // Séparées par ";"
    private MultipartFile csvFile;
}