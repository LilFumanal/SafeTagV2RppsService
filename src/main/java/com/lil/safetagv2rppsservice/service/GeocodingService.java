package com.lil.safetagv2rppsservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lil.safetagv2rppsservice.entity.PracticeLocation;
import com.lil.safetagv2rppsservice.event.AddressGeocodedEvent; // NOUVEAU
import com.lil.safetagv2rppsservice.repository.PracticeLocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate; // NOUVEAU
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeocodingService {

    private final PracticeLocationRepository repository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate; // NOUVEAU

    @Value("${rpps.source.geocodingAPIUrl}")
    private String banApiUrl;

    @Value("${rabbitmq.exchange.name:safetag.exchange}") // NOUVEAU
    private String exchangeName;

    @Value("${rabbitmq.routing.geocoded:address.geocoded}") // NOUVEAU
    private String routingKey;

    public int processGeocodingBatch() {
        List<PracticeLocation> locations = repository.findTop50ByGeocodingAttemptedFalse();

        if (locations.isEmpty()) {
            log.info("Aucune adresse à géocoder.");
            return 0;
        }
        log.info("Géocodage d'un lot de " + locations.size() + " adresses...");
        int successCount = 0;

        for (PracticeLocation loc : locations) {
            loc.setGeocodingAttempted(true);

            String query = buildAddressQuery(loc);
            if (query.isBlank()) {
                continue;
            }

            try {
                String responseBody = restTemplate.getForObject(banApiUrl , String.class, query);

                if (responseBody != null) {
                    JsonNode response = objectMapper.readTree(responseBody);

                    if (response.has("features") && response.get("features").size() > 0) {
                        JsonNode coordinates = response.get("features").get(0).get("geometry").get("coordinates");
                        loc.setLongitude(coordinates.get(0).asDouble());
                        loc.setLatitude(coordinates.get(1).asDouble());

                        // NOUVEAU : Publication de l'événement
                        publishGeocodedEvent(loc);

                        successCount++; // Incrémente les succès
                    } else {
                        log.warn("Aucun résultat trouvé pour l'adresse ID {}: {}", loc.getId(), query);
                    }
                }
            } catch (Exception e) {
                log.error("Erreur lors du géocodage de l'adresse ID {}: {}", loc.getId(), e.getMessage());
            }
        }

        repository.saveAll(locations);
        log.info("Batch terminé : {} adresses trouvées sur {} tentées.", successCount, locations.size());

        return locations.size();
    }

    private String buildAddressQuery(PracticeLocation loc) {
        StringBuilder query = new StringBuilder();
        if (loc.getStreetNumber() != null) query.append(loc.getStreetNumber()).append(" ");
        if (loc.getStreet() != null) query.append(loc.getStreet()).append(" ");
        if (loc.getZipCode() != null) query.append(loc.getZipCode()).append(" ");
        if (loc.getCity() != null) query.append(loc.getCity());

        return query.toString().trim();
    }

    public void geocodeLocation(UUID locationId) {
        PracticeLocation loc = repository.findById(locationId).orElse(null);

        if (loc == null || Boolean.TRUE.equals(loc.isGeocodingAttempted())) {
            return;
        }

        loc.setGeocodingAttempted(true);
        String query = buildAddressQuery(loc);

        if (query.isBlank()) {
            repository.save(loc);
            return;
        }

        try {
            String responseBody = restTemplate.getForObject(banApiUrl, String.class, query);
            if (responseBody != null) {
                JsonNode response = objectMapper.readTree(responseBody);
                if (response.has("features") && response.get("features").size() > 0) {
                    JsonNode coordinates = response.get("features").get(0).get("geometry").get("coordinates");
                    loc.setLongitude(coordinates.get(0).asDouble());
                    loc.setLatitude(coordinates.get(1).asDouble());

                    // NOUVEAU : Publication de l'événement
                    publishGeocodedEvent(loc);

                    log.info("Adresse ID {} géocodée avec succès.", locationId);
                } else {
                    log.warn("Aucun résultat de géocodage pour l'adresse ID {}", locationId);
                }
            }
        } catch (Exception e) {
            log.error("Erreur lors du géocodage unitaire de l'ID {}: {}", locationId, e.getMessage());
        }

        repository.save(loc);
    }

    // NOUVEAU : Méthode extraite pour éviter la duplication
    private void publishGeocodedEvent(PracticeLocation loc) {
        AddressGeocodedEvent event = new AddressGeocodedEvent(
                loc.getId(),
                loc.getLatitude(),
                loc.getLongitude(),
                loc.getStreet(),
                loc.getZipCode(),
                loc.getCity()
        );
        rabbitTemplate.convertAndSend(exchangeName, routingKey, event);
    }
}
