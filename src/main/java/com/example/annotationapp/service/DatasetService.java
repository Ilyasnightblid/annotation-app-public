package com.example.annotationapp.service;

import com.example.annotationapp.dto.DatasetCreateDto;
import com.example.annotationapp.entity.Dataset;
import com.example.annotationapp.entity.TextPair;
import com.example.annotationapp.entity.User;
import com.example.annotationapp.repository.DatasetRepository;
import com.example.annotationapp.repository.TextPairRepository;
import com.example.annotationapp.repository.AnnotationRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class DatasetService {

    @Autowired
    private DatasetRepository datasetRepository;

    @Autowired
    private TextPairRepository textPairRepository;

    @Autowired
    private AnnotationRepository annotationRepository;


    @Transactional // Important pour gérer la transaction sur plusieurs opérations BDD
    public Dataset createDataset(DatasetCreateDto dto) throws Exception {
        if (datasetRepository.findByName(dto.getName()).isPresent()) {
            throw new IllegalArgumentException("Dataset name already exists: " + dto.getName());
        }

        Dataset dataset = new Dataset(dto.getName(), dto.getDescription(), dto.getPossibleClasses());
        Dataset savedDataset = datasetRepository.save(dataset);

        List<TextPair> textPairs = parseCsvFile(dto.getCsvFile(), savedDataset);
        textPairRepository.saveAll(textPairs);
        savedDataset.setTextPairs(textPairs);

        return savedDataset;
    }

    private List<TextPair> parseCsvFile(MultipartFile file, Dataset dataset) throws Exception {
        List<TextPair> textPairs = new ArrayList<>();
        try (Reader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                    .withFirstRecordAsHeader() // Si ton CSV a des entêtes
                    // .withHeader("Text1", "Text2") // Si pas d'entête, mais tu connais les colonnes
                    .withIgnoreHeaderCase()
                    .withTrim());

            for (CSVRecord csvRecord : csvParser) {
                // Assure-toi que les noms de colonnes correspondent à ton CSV
                // ou utilise les indices si pas d'entête (csvRecord.get(0), csvRecord.get(1))
                String text1 = csvRecord.get("Text1"); // Ou le nom de ta première colonne
                String text2 = csvRecord.get("Text2"); // Ou le nom de ta deuxième colonne
                textPairs.add(new TextPair(text1, text2, dataset));
            }
        }
        return textPairs;
    }

    public List<Dataset> getAllDatasets() {
        return datasetRepository.findAll();
    }

    public Optional<Dataset> getDatasetById(Long id) {
        return datasetRepository.findById(id);
    }

    public List<TextPair> getTextPairsByDataset(Dataset dataset) {
        return textPairRepository.findByDataset(dataset);
    }

    public long countAnnotatedTextPairs(Dataset dataset) {
        return dataset.getAnnotations().stream().filter(a -> a.getChosenClass() != null).count();
    }

    public long getTotalTextPairs(Dataset dataset) {
        return textPairRepository.findByDataset(dataset).size();
    }
}