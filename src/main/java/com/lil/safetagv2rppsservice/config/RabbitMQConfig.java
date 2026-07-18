package com.lil.safetagv2rppsservice.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Nom de la file d'attente pour le géocodage
    public static final String GEOCODING_QUEUE = "rpps.geocoding.queue";

    @Bean
    public Queue geocodingQueue() {
        // durable = true permet de conserver les messages en attente même si RabbitMQ redémarre
        return new Queue(GEOCODING_QUEUE, true);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        // Permet d'envoyer et recevoir des objets (et des UUID) convertis automatiquement en JSON
        return new Jackson2JsonMessageConverter();
    }
}
