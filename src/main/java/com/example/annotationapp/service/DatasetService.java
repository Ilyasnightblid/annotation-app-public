package com.example.annotationapp.service;

import com.example.annotationapp.dto.DatasetCreateDto;
import com.example.annotationapp.entity.Dataset;
import com.example.annotationapp.entity.TextPair;
// Pas besoin d'importer User ici si non utilisé directement
// import com.example.annotationapp.entity.User;
import com.example.annotationapp.repository.DatasetRepository;
import com.example.annotationapp.repository.TextPairRepository;
// Pas besoin d'AnnotationRepository ici s'il n'est pas utilisé pour la création de dataset
// import com.example.annotationapp.repository.AnnotationRepository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger; // Ajout pour le logging
import org.slf4j.LoggerFactory; // Ajout pour le logging
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class DatasetService {

    private static final Logger logger = LoggerFactory.getLogger(DatasetService.class);

    private final DatasetRepository datasetRepository;
    private final TextPairRepository textPairRepository;
    // ObjectMapper peut être final si initialisé ici, ou injecté si configuré comme Bean Spring
    private final ObjectMapper objectMapper;

    @Autowired
    public DatasetService(DatasetRepository datasetRepository,
                          TextPairRepository textPairRepository) {
        this.datasetRepository = datasetRepository;
        this.textPairRepository = textPairRepository;
        this.objectMapper = new ObjectMapper(); // Initialisation de ObjectMapper
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
            throw new IllegalArgumentException("Unsupported file type. Please upload a CSV or JSON file. Received: " + originalFilename);
        }

        if(textPairs.isEmpty()){
            logger.warn("The uploaded file {} is empty or does not contain valid Text1/Text2 pairs.", originalFilename);
            // Optionnellement, supprimer le dataset si aucun TextPair n'est trouvé
            // datasetRepository.delete(savedDataset);
            throw new IllegalArgumentException("The uploaded file is empty or does not contain valid Text1/Text2 pairs.");
        }

        logger.info("Saving {} text pairs for dataset ID: {}", textPairs.size(), savedDataset.getId());
        textPairRepository.saveAll(textPairs);

        // Pour que l'objet Dataset retourné par la méthode contienne les TextPairs,
        // il faut explicitement les setter si la session Hibernate est déjà flushée ou si on veut être sûr.
        // Cependant, si la relation est bien gérée et que l'entité dataset est toujours managée,
        // les textPairs y seront liés. Pour être explicite :
        // savedDataset.setTextPairs(textPairs); // Ceci n'est généralement pas nécessaire si textPairs ont une réf à savedDataset

        logger.info("Dataset '{}' created successfully with {} text pairs.", savedDataset.getName(), textPairs.size());
        // Re-fetch pour s'assurer que la collection textPairs est chargée si besoin (ou utiliser EAGER fetch)
        return datasetRepository.findById(savedDataset.getId()).orElseThrow(() -> new RuntimeException("Failed to re-fetch dataset after creation."));
    }

    private List<TextPair> parseCsvFile(MultipartFile file, Dataset dataset) throws Exception {
        List<TextPair> textPairs = new ArrayList<>();
        // Utilisation de try-with-resources pour InputStreamReader et BufferedReader
        try (InputStream inputStream = file.getInputStream();
             InputStreamReader inputStreamReader = new InputStreamReader(inputStream); // Spécifier l'encodage si besoin, ex: StandardCharsets.UTF_8
             Reader reader = new BufferedReader(inputStreamReader);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                     .withFirstRecordAsHeader()
                     .withIgnoreHeaderCase()
                     .withTrim())) {

            if (!csvParser.getHeaderMap().containsKey("Text1") || !csvParser.getHeaderMap().containsKey("Text2")) {
                logger.error("CSV file for dataset '{}' is missing required headers 'Text1' or 'Text2'. Found headers: {}", dataset.getName(), csvParser.getHeaderMap().keySet());
                throw new IllegalArgumentException("CSV file must contain 'Text1' and 'Text2' headers.");
            }

            for (CSVRecord csvRecord : csvParser) {
                String text1 = csvRecord.get("Text1");
                String text2 = csvRecord.get("Text2");
                // Ajout d'une vérification pour s'assurer que les textes ne sont pas vides après trim
                if (text1 != null && !text1.trim().isEmpty() && text2 != null && !text2.trim().isEmpty()) {
                    textPairs.add(new TextPair(text1.trim(), text2.trim(), dataset));
                } else {
                    logger.warn("Skipping empty or invalid row in CSV for dataset '{}': Text1='{}', Text2='{}'", dataset.getName(), text1, text2);
                }
            }
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
            // TypeReference aide Jackson à désérialiser une liste d'objets génériques
            List<TextPairData> rawData = objectMapper.readValue(inputStream, new TypeReference<List<TextPairData>>() {});
            for (TextPairData data : rawData) {
                // Vérification pour s'assurer que les textes ne sont pas nuls ou vides après trim
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
    // Les getters et setters sont nécessaires pour Jackson.
    // Lombok pourrait être utilisé ici aussi avec @Getter @Setter @NoArgsConstructor
    private static class TextPairData {
        private String text1;
        private String text2;

        public String getText1() { return text1; }
        public void setText1(String text1) { this.text1 = text1; }
        public String getText2() { return text2; }
        public void setText2(String text2) { this.text2 = text2; }
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
        // Cette méthode dépend de la façon dont les annotations sont liées au Dataset.
        // Si Dataset a une collection @OneToMany List<Annotation> annotations:
        if (dataset.getAnnotations() == null) return 0;
        return dataset.getAnnotations().stream().filter(a -> a.getChosenClass() != null && !a.getChosenClass().trim().isEmpty()).count();
        // Sinon, si tu dois passer par AnnotationRepository :
        // return annotationRepository.countByDatasetAndChosenClassIsNotNull(dataset); // Tu devrais créer cette méthode dans AnnotationRepository
    }

    public long getTotalTextPairs(Dataset dataset) {
        return textPairRepository.countByDataset(dataset); // Plus efficace que de charger toute la liste
    }
}