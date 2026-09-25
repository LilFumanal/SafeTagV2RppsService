package com.lil.safetagv2rppsservice.consumer;

import com.lil.safetagv2rppsservice.config.RabbitMQConfig;
import com.lil.safetagv2rppsservice.service.GeocodingService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
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
    public void receiveGeocodingMessage(UUID locationId, Channel channel,
                                        @Header(name = AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("Message reçu pour géocodage de l'adresse ID: {}", locationId);
        try {
            geocodingService.geocodeLocation(locationId);
            Thread.sleep(100);
            channel.basicAck(deliveryTag, false); // ✅ acknowledge success
            log.info("Géocodage réussi pour ID: {}", locationId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Le thread de géocodage a été interrompu pour ID: {}", locationId);
            try {
                channel.basicNack(deliveryTag, false, true); // ❌ requeue
            } catch (IOException ex) {
                log.error("Erreur lors du Nack", ex);
            }
        } catch (Exception e) {
            log.error("Échec du traitement du message de géocodage pour l'ID {}: {}", locationId, e.getMessage());
            try {
                channel.basicNack(deliveryTag, false, true); // ❌ requeue
            } catch (IOException ex) {
                log.error("Erreur lors du Nack", ex);
            }
        }
    }
}