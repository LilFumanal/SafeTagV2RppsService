package com.lil.safetagv2rppsservice.controller;

import com.lil.safetagv2rppsservice.config.RabbitMQConfig;
import com.lil.safetagv2rppsservice.service.GeocodingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/geocoding")
@RequiredArgsConstructor
public class GeocodingController {

    private final GeocodingService geocodingService;
    private final RabbitTemplate rabbitTemplate;

    @PostMapping("/trigger")
    public ResponseEntity<String> triggerGeocoding() {
        int processedCount = geocodingService.processGeocodingBatch();
        return ResponseEntity.ok("Batch de géocodage terminé. " + processedCount + " adresses traitées.");
    }

    // Endpoint de test pour simuler l'envoi d'un message RabbitMQ
    @PostMapping("/test-publish")
    public ResponseEntity<String> testPublishGeocodingMessage(@RequestParam UUID locationId) {
        log.info("Envoi d'un message de test dans la queue pour l'ID: {}", locationId);

        // On envoie directement l'UUID dans la file d'attente
        rabbitTemplate.convertAndSend(RabbitMQConfig.GEOCODING_QUEUE, locationId);

        return ResponseEntity.ok("Message de test envoyé pour l'ID " + locationId);
    }
}
