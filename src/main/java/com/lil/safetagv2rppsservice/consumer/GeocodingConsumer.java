package com.lil.safetagv2rppsservice.consumer;

import com.lil.safetagv2rppsservice.config.RabbitMQConfig;
import com.lil.safetagv2rppsservice.service.GeocodingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeocodingConsumer {

    private final GeocodingService geocodingService;
    @RabbitListener(
            queues = RabbitMQConfig.GEOCODING_QUEUE,
            concurrency = "1"
    )
    public void receiveGeocodingMessage(UUID locationId) {
        log.info("Message reçu pour géocodage de l'adresse ID: {}", locationId);
        try {
            geocodingService.geocodeLocation(locationId);
            Thread.sleep(100);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Le thread de géocodage a été interrompu");
        } catch (Exception e) {
            log.error("Échec du traitement du message de géocodage pour l'ID {}: {}", locationId, e.getMessage());
        }
    }
}
