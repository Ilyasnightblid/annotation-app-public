package com.example.annotationapp.dto.export;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AnnotationExportDto {
    private Long annotationId;
    private String datasetName;
    private Long textPairId;
    private String text1;
    private String text2;
    private String chosenClass;
    private AnnotatorExportDto annotator;

    public AnnotationExportDto(Long annotationId, String datasetName, Long textPairId, String text1, String text2, String chosenClass, AnnotatorExportDto annotator) {
        this.annotationId = annotationId;
        this.datasetName = datasetName;
        this.textPairId = textPairId;
        this.text1 = text1;
        this.text2 = text2;
        this.chosenClass = (chosenClass != null) ? chosenClass : "NOT_ANNOTATED";
        this.annotator = annotator;
    }
}