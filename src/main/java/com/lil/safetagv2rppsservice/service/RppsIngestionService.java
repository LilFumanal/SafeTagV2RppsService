package com.lil.safetagv2rppsservice.service;

import com.lil.safetagv2rppsservice.client.CSVClient;
import com.lil.safetagv2rppsservice.config.RabbitMQConfig;
import com.lil.safetagv2rppsservice.entity.PracticeLocation;
import com.lil.safetagv2rppsservice.entity.RppsPractitioner;
import com.lil.safetagv2rppsservice.repository.RppsPractitionerRepository;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class RppsIngestionService {
    @Value("${safetag.rpps.import.limit:-1}")
    private int importLimit;

    // Constantes métier - CODES PROFESSION SANTÉ MENTALE & ADDICTOLOGIE
    // 10 = Médecin (inclut psychiatres avec spécialités SM04/SM54)
    // 93 = Psychologue (titulaire du titre protégé)
    // 94 = Ergothérapeute (rééducation psychomotrice et cognitive)
    // 95 = Psychomotricien (troubles psychomoteurs et relationnels)
    private static final List<String> ALLOWED_ROLES = List.of("10", "93", "94", "95");
    private static final Map<String, String> PROFESSION_LABELS = Map.of(
            "10", "Psychiatre",
            "93", "Psychologue",
            "94", "Ergothérapeute",
            "95", "Psychomotricien"
    );

    // CODES SPÉCIALITÉ/SAVOIR-FAIRE (préfixe SM)
    // SM04 = Psychiatrie générale (adultes)
    // SM54 = Psychiatrie enfant/ado
    // SM33 = Addictologie
    // SM93 = Psychologie clinique
    // SM26 = Thérapie familiale et de couple
    // SM53 = Pédopsychiatrie (ancienne dénomination)
    // SM70 = Sexologie et médecine sexuelle
    // SM65 = Hypnose médicale thérapeutique
    private static final List<String> ALLOWED_SPECIALTIES = List.of(
            "SM04", "SM54", "SM33", "SM93", "SM26", "SM53",  "SM70", "SM65"
    );
    private static final Map<String, String> SPECIALTY_LABELS = Map.of(
            "SM04", "Psychiatre",
            "SM54", "Pédopsychiatre",
            "SM33", "Addictologue",
            "SM93", "Psychologue clinicien",
            "SM26", "Thérapeute familial",
            "SM53", "Pédopsychiatre (anc.)",
            "SM70", "Sexologue",
            "SM65", "Hypnothérapeute"
    );
    // Index de base
    private static final int COL_ID_PP = 1;           // Identifiant PP
    private static final int COL_LASTNAME = 7;        // Nom d'exercice
    private static final int COL_FIRSTNAME = 8;       // Prénom d'exercice
    private static final int COL_PROFESSION_CODE = 9; // Code profession
    private static final int COL_SPECIALTY_CODE = 15; // Code savoir-faire
    private static final int COL_LIBELLE_EXERCICE_MODE = 18; //Libellé mode d'exercice

    // Index d'adresse
    private static final int COL_FACILITY_NAME = 24;  // Raison sociale (structure)
    private static final int COL_STREET_NUM = 28;     // Numéro Voie
    private static final int COL_STREET_REP = 29;     // Indice de répétition (bis, ter...)
    private static final int COL_STREET_TYPE = 31;    // Libellé type de voie
    private static final int COL_STREET_NAME = 32;    // Libellé Voie
    private static final int COL_ZIP_CODE = 35;       // Code postal
    private static final int COL_CITY = 37;           // Bureau distributeur (Ville)

    private static final int BATCH_SIZE = 1000;
    private final RppsPractitionerRepository repository;
    private final CSVClient csvClient;
    private final RabbitTemplate rabbitTemplate;

    public RppsIngestionService(RppsPractitionerRepository repository, CSVClient csvClient, RabbitTemplate rabbitTemplate) {
        this.repository = repository;
        this.csvClient = csvClient;
        this.rabbitTemplate = rabbitTemplate;
    }

    public void importRppsData() {
        log.info("[START] Début de l'importation...");
        Map<String, RppsPractitioner> batchMap = new HashMap<>();
        int totalSaved = 0;

        try {
            // 1. Téléchargement dans un fichier temporaire local pour éviter le HttpTimeoutException
            Path tempFile = Files.createTempFile("rpps_extract", ".csv");
            log.info("Téléchargement du fichier RPPS en cours vers {}...", tempFile.toAbsolutePath());

            try (InputStream rawStream = csvClient.downloadRppsFile()) {
                Files.copy(rawStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("Téléchargement terminé. Début du traitement en base de données...");

            // 2. Lecture depuis le fichier local
            try (InputStream fileStream = Files.newInputStream(tempFile);
                 CSVReader csvReader = new CSVReaderBuilder(new InputStreamReader(fileStream, StandardCharsets.UTF_8))
                         .withCSVParser(new CSVParserBuilder().withSeparator('|').build())
                         .withSkipLines(1)
                         .build()) {

                String[] row;
                int lineCount = 0;

                while ((row = csvReader.readNext()) != null) {
                    // BLOC DE TEST : Arrêt après 100 lignes
                /*
                if (importLimit > 0 && lineCount >= importLimit) {
                    log.info("[DEBUG] Limite de {} lignes atteinte, arrêt de la lecture.", importLimit);
                    break;
                }
                lineCount++;
                */
                    if (!isValid(row)) continue;

                    String rppsId = row[COL_ID_PP];
                    // 2. Formatage des données (uniquement pour les lignes valides)
                    String street = String.format("%s %s %s",
                            getValueOrEmpty(row, COL_STREET_REP),
                            getValueOrEmpty(row, COL_STREET_TYPE),
                            getValueOrEmpty(row, COL_STREET_NAME)
                    ).replaceAll("\\s+", " ").trim();

                    PracticeLocation location = new PracticeLocation();
                    location.setStreetNumber(getValueOrEmpty(row, COL_STREET_NUM));
                    location.setFacilityName(getValueOrEmpty(row, COL_FACILITY_NAME));
                    location.setStreet(street.isEmpty() ? null : street);
                    location.setZipCode(getValueOrEmpty(row, COL_ZIP_CODE));
                    location.setCity(getValueOrEmpty(row, COL_CITY));

                    // 3. Ajout au batch
                    if (batchMap.containsKey(rppsId)) {
                        // Le praticien est déjà dans le lot (mémoire), on ajoute l'adresse
                        RppsPractitioner existingPractitioner = batchMap.get(rppsId);
                        location.setRppsPractitioner(existingPractitioner);
                        existingPractitioner.getLocations().add(location);
                    } else {
                        // On vérifie d'abord si le praticien existe DEJA en base de données
                        Optional<RppsPractitioner> existingDbOpt = repository.findByRppsId(rppsId);

                        if (existingDbOpt.isPresent()) {
                            // Mise à jour (Upsert)
                            RppsPractitioner existingInDb = existingDbOpt.get();
                            location.setRppsPractitioner(existingInDb);
                            existingInDb.getLocations().add(location);
                            batchMap.put(rppsId, existingInDb);
                        } else {
                            // Création (Nouveau)
                            RppsPractitioner newPractitioner = mapToEntity(row, location);
                            batchMap.put(rppsId, newPractitioner);
                        }
                    }


                    // 4. Sauvegarde si le batch est plein
                    if (batchMap.size() >= BATCH_SIZE) {
                        List<RppsPractitioner> savedBatch = repository.saveAll(batchMap.values());
                        publishGeocodingMessages(savedBatch);
                        totalSaved += batchMap.size();
                        log.info("Batch sauvegardé. Total praticiens en cours : {}", totalSaved);
                        batchMap.clear();
                    }
                }

                // 5. Reliquat
                if (!batchMap.isEmpty()) {
                    repository.saveAll(batchMap.values());
                    totalSaved += batchMap.size();
                }

                log.info("[SUCCESS] Importation terminée. {} praticiens insérés ou mis à jour.", totalSaved);

            } catch (Exception e) {
                log.error("Erreur critique lors de l'importation", e);
                throw new RuntimeException("L'importation a échoué : " + e.getMessage());
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

        private RppsPractitioner mapToEntity(String[] row, PracticeLocation location) {
        RppsPractitioner p = new RppsPractitioner();
        p.setRppsId(row[COL_ID_PP]);
        p.setName(row[COL_LASTNAME]+" "+row[COL_FIRSTNAME]);
        p.setProfessionCode(row[COL_PROFESSION_CODE]);
        p.setSpecialtyCode(row[COL_SPECIALTY_CODE]);
        p.setExerciceMode(row[COL_LIBELLE_EXERCICE_MODE]);
        p.setLastUpdated(LocalDateTime.now());
        p.addLocation(location);
        return p;
    }

    private boolean isValid(String[] row) {
        if (row == null || row.length < 55) {
            return false;
        }

        String professionCode = row[COL_PROFESSION_CODE];

        // 1. Si le métier n'est pas dans notre liste globale, on rejette
        if (!ALLOWED_ROLES.contains(professionCode)) {
            return false;
        }

        // 2. Pour 93 (Psychologue), 94 (Ergo) et 95 (Psychomotricien), on valide directement
        if ("93".equals(professionCode) || "94".equals(professionCode) || "95".equals(professionCode)) {
            return true;
        }

        // 3. Pour 10 (Médecin), on vérifie obligatoirement la spécialité (Psychiatrie/Addicto)
        if ("10".equals(professionCode)) {
            String specialtyCode = row[COL_SPECIALTY_CODE];
            return ALLOWED_SPECIALTIES.contains(specialtyCode);
        }

        return false;
    }


    private String getValueOrEmpty(String[] row, int index) {
        return (row.length > index && row[index] != null) ? row[index].trim() : "";
    }

    private void publishGeocodingMessages(Collection<RppsPractitioner> practitioners) {
        practitioners.stream()
                .flatMap(p -> p.getLocations().stream())
                // On ne géocode que les adresses qui n'ont pas encore de coordonnées (nouvelles ou modifiées)
                .filter(loc -> loc.getId() != null && loc.getLatitude() == null)
                .forEach(loc -> {
                    try {
                        rabbitTemplate.convertAndSend(RabbitMQConfig.GEOCODING_QUEUE, loc.getId());
                    } catch (Exception e) {
                        log.error("Impossible d'envoyer l'adresse {} au géocodage : {}", loc.getId(), e.getMessage());
                    }
                });
    }
}
