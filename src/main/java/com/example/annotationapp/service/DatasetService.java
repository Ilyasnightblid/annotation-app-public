package com.example.annotationapp.service;

import com.example.annotationapp.dto.DatasetCreateDto;
import com.example.annotationapp.entity.Dataset;
import com.example.annotationapp.entity.TextPair;
// AnnotationRepository est nécessaire pour la suppression si on veut être plus explicite,
// ou si la cascade n'est pas suffisante pour TOUS les cas (mais elle devrait l'être ici).
import com.example.annotationapp.repository.AnnotationRepository;
import com.example.annotationapp.repository.DatasetRepository;
import com.example.annotationapp.repository.TextPairRepository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets; // Bonne pratique pour spécifier l'encodage
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class DatasetService {

    private static final Logger logger = LoggerFactory.getLogger(DatasetService.class);

    private final DatasetRepository datasetRepository;
    private final TextPairRepository textPairRepository;
    private final AnnotationRepository annotationRepository; // Ajouté pour la suppression explicite si besoin, ou pour d'autres logiques
    private final ObjectMapper objectMapper;

    @Autowired
    public DatasetService(DatasetRepository datasetRepository,
                          TextPairRepository textPairRepository,
                          AnnotationRepository annotationRepository) { // Injection d'AnnotationRepository
        this.datasetRepository = datasetRepository;
        this.textPairRepository = textPairRepository;
        this.annotationRepository = annotationRepository; // Initialisation
        this.objectMapper = new ObjectMapper();
    }

    @Transactional
    public Dataset createDataset(DatasetCreateDto dto) throws Exception {
        logger.info("Attempting to create dataset with name: {}", dto.getName());
        if (datasetRepository.findByName(dto.getName()).isPresent()) {
            logger.warn("Dataset name already exists: {}", dto.getName());
            throw new IllegalArgumentException("Dataset name already exists: " + dto.getName());
        }

        Dataset dataset = new Dataset(dto.getName(), dto.getDescription(), dto.getPossibleClasses());
        Dataset savedDataset = datasetRepository.save(dataset);
        logger.info("Dataset entity saved with ID: {}", savedDataset.getId());

        List<TextPair> textPairs;
        MultipartFile file = dto.getCsvFile(); // Le DTO actuel nomme le champ csvFile

        if (file == null || file.isEmpty()) {
            logger.error("No file uploaded for dataset creation.");
            // Si aucun fichier n'est téléchargé, supprimer le dataset qui vient d'être créé
            datasetRepository.delete(savedDataset);
            throw new IllegalArgumentException("No file uploaded. Please select a CSV or JSON file.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && originalFilename.toLowerCase().endsWith(".json")) {
            logger.info("Parsing JSON file: {}", originalFilename);
            textPairs = parseJsonFile(file.getInputStream(), savedDataset);
        } else if (originalFilename != null && originalFilename.toLowerCase().endsWith(".csv")) {
            logger.info("Parsing CSV file: {}", originalFilename);
            textPairs = parseCsvFile(file, savedDataset);
        } else {
            logger.error("Unsupported file type: {}", originalFilename);
            datasetRepository.delete(savedDataset); // Nettoyage
            throw new IllegalArgumentException("Unsupported file type. Please upload a CSV or JSON file. Received: " + originalFilename);
        }

        if(textPairs.isEmpty()){
            logger.warn("The uploaded file {} is empty or does not contain valid Text1/Text2 pairs.", originalFilename);
            datasetRepository.delete(savedDataset); // Nettoyage
            throw new IllegalArgumentException("The uploaded file is empty or does not contain valid Text1/Text2 pairs.");
        }

        logger.info("Saving {} text pairs for dataset ID: {}", textPairs.size(), savedDataset.getId());
        textPairRepository.saveAll(textPairs);

        // Mise à jour de la collection dans l'entité dataset managée pour qu'elle soit à jour
        // Bien que saveAll sur textPairs mette à jour la base, l'instance 'savedDataset' en mémoire
        // peut ne pas avoir sa collection 'textPairs' automatiquement peuplée sans un re-fetch ou un merge.
        // Alternativement, si la relation est bidirectionnelle et gérée correctement, cela peut ne pas être nécessaire.
        // Pour être sûr, on peut recharger l'entité.
        savedDataset.setTextPairs(textPairs); // Si tu veux que l'objet retourné soit complet immédiatement

        logger.info("Dataset '{}' created successfully with {} text pairs.", savedDataset.getName(), textPairs.size());
        // Retourner l'entité avec la collection potentiellement mise à jour
        // ou re-fetcher pour garantir l'état le plus récent de la base.
        // return datasetRepository.findById(savedDataset.getId()).orElseThrow(() -> new RuntimeException("Failed to re-fetch dataset after creation."));
        return savedDataset; // Retourne l'entité managée, Hibernate gère les collections
    }

    private List<TextPair> parseCsvFile(MultipartFile file, Dataset dataset) throws Exception {
        List<TextPair> textPairs = new ArrayList<>();
        try (InputStream inputStream = file.getInputStream();
             InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8); // Spécifier UTF-8
             Reader reader = new BufferedReader(inputStreamReader);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                     .withFirstRecordAsHeader()
                     .withIgnoreHeaderCase()
                     .withTrim())) {

            if (!csvParser.getHeaderMap().containsKey("text1") || !csvParser.getHeaderMap().containsKey("text2")) { // Normalisé en minuscule par withIgnoreHeaderCase
                logger.error("CSV file for dataset '{}' is missing required headers 'Text1' or 'Text2'. Found headers: {}", dataset.getName(), csvParser.getHeaderMap().keySet());
                throw new IllegalArgumentException("CSV file must contain 'Text1' and 'Text2' headers.");
            }

            for (CSVRecord csvRecord : csvParser) {
                String text1 = csvRecord.get("Text1"); // Garder la casse originale pour le get si withIgnoreHeaderCase fonctionne bien pour le mapping
                String text2 = csvRecord.get("Text2");
                if (text1 != null && !text1.trim().isEmpty() && text2 != null && !text2.trim().isEmpty()) {
                    textPairs.add(new TextPair(text1.trim(), text2.trim(), dataset));
                } else {
                    logger.warn("Skipping empty or invalid row in CSV for dataset '{}': Text1='{}', Text2='{}'", dataset.getName(), text1, text2);
                }
            }
        } catch (IllegalArgumentException e) { // Capturer spécifiquement l'erreur de header manquant
            logger.error("Header validation failed for CSV: {}", e.getMessage());
            throw e; // Relauncher pour que le contrôleur l'attrape
        } catch (Exception e) {
            logger.error("Error parsing CSV file for dataset '{}': {}", dataset.getName(), e.getMessage(), e);
            throw new Exception("Failed to parse CSV file: " + e.getMessage(), e);
        }
        logger.info("Parsed {} text pairs from CSV for dataset '{}'.", textPairs.size(), dataset.getName());
        return textPairs;
    }

    private List<TextPair> parseJsonFile(InputStream inputStream, Dataset dataset) throws Exception {
        List<TextPair> textPairs = new ArrayList<>();
        try {
            List<TextPairData> rawData = objectMapper.readValue(inputStream, new TypeReference<List<TextPairData>>() {});
            for (TextPairData data : rawData) {
                if (data.getText1() != null && !data.getText1().trim().isEmpty() &&
                        data.getText2() != null && !data.getText2().trim().isEmpty()) {
                    textPairs.add(new TextPair(data.getText1().trim(), data.getText2().trim(), dataset));
                } else {
                    logger.warn("Skipping empty or invalid entry in JSON for dataset '{}': Text1='{}', Text2='{}'", dataset.getName(), data.getText1(), data.getText2());
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing JSON file for dataset '{}': {}", dataset.getName(), e.getMessage(), e);
            throw new Exception("Failed to parse JSON file: " + e.getMessage(), e);
        }
        logger.info("Parsed {} text pairs from JSON for dataset '{}'.", textPairs.size(), dataset.getName());
        return textPairs;
    }

    // Classe interne statique pour le mapping JSON.
    // Jackson utilise les conventions de nommage (get/set) ou les champs publics.
    // Si tu utilises Lombok ici : @Getter @Setter @NoArgsConstructor
    private static class TextPairData {
        private String text1;
        private String text2;

        public String getText1() { return text1; }
        public void setText1(String text1) { this.text1 = text1; }
        public String getText2() { return text2; }
        public void setText2(String text2) { this.text2 = text2; }
    }


    public List<Dataset> getAllDatasets() {
        logger.debug("Fetching all datasets");
        return datasetRepository.findAll();
    }

    public Optional<Dataset> getDatasetById(Long id) {
        logger.debug("Fetching dataset by ID: {}", id);
        return datasetRepository.findById(id);
    }

    public List<TextPair> getTextPairsByDataset(Dataset dataset) {
        logger.debug("Fetching text pairs for dataset ID: {}", dataset.getId());
        return textPairRepository.findByDataset(dataset);
    }

    public long countAnnotatedTextPairs(Dataset dataset) {
        if (dataset == null || dataset.getId() == null) return 0; // Vérification de nullité
        logger.debug("Counting annotated text pairs for dataset ID: {}", dataset.getId());
        // S'assurer que getAnnotations() ne renvoie pas null si la collection n'est pas initialisée (LAZY fetch)
        // Il serait plus robuste de faire une requête count dédiée si la collection est grande ou LAZY.
        // Mais pour l'instant, en se basant sur la structure actuelle :
        Dataset managedDataset = datasetRepository.findById(dataset.getId()).orElse(null);
        if (managedDataset == null || managedDataset.getAnnotations() == null) return 0;
        return managedDataset.getAnnotations().stream()
                .filter(a -> a.getChosenClass() != null && !a.getChosenClass().trim().isEmpty())
                .count();
    }

    public long getTotalTextPairs(Dataset dataset) {
        if (dataset == null || dataset.getId() == null) return 0; // Vérification de nullité
        logger.debug("Counting total text pairs for dataset ID: {}", dataset.getId());
        return textPairRepository.countByDataset(dataset);
    }

    // NOUVELLE MÉTHODE DE SUPPRESSION
    @Transactional
    public void deleteDataset(Long datasetId) {
        logger.info("Attempting to delete dataset with ID: {}", datasetId);
        if (!datasetRepository.existsById(datasetId)) {
            logger.warn("Dataset with ID: {} not found for deletion.", datasetId);
            throw new IllegalArgumentException("Dataset not found with ID: " + datasetId);
        }
        // Grâce à CascadeType.ALL et orphanRemoval=true sur les relations
        // dans l'entité Dataset (pour textPairs et annotations),
        // la suppression du Dataset devrait entraîner la suppression en cascade.
        datasetRepository.deleteById(datasetId);
        logger.info("Dataset with ID: {} deleted successfully.", datasetId);
    }
}