package com.lil.safetagv2rppsservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lil.safetagv2rppsservice.entity.PracticeLocation;
import com.lil.safetagv2rppsservice.repository.PracticeLocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GeocodingServiceTest {

    @Mock
    private PracticeLocationRepository repository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private RabbitTemplate rabbitTemplate;

    // On utilise un vrai ObjectMapper pour s'épargner des mocks JSON trop complexes
    private final ObjectMapper objectMapper = new ObjectMapper();

    private GeocodingService geocodingService;

    @BeforeEach
    void setUp() {
        // Injection manuelle pour le test unitaire
        geocodingService = new GeocodingService(repository, restTemplate, objectMapper, rabbitTemplate);
        ReflectionTestUtils.setField(geocodingService, "banApiUrl", "https://api-adresse.data.gouv.fr/search/");
    }

    @Test
    void processGeocodingBatch_NoLocations_ShouldReturnZero() {
        // GIVEN
        when(repository.findTop50ByGeocodingAttemptedFalse()).thenReturn(Collections.emptyList());

        // WHEN
        int result = geocodingService.processGeocodingBatch();

        // THEN
        assertEquals(0, result);
        verify(restTemplate, never()).getForObject(anyString(), eq(String.class), anyString());
    }

    @Test
    void processGeocodingBatch_WithValidResponse_ShouldUpdateCoordinates() {
        // GIVEN
        PracticeLocation location = new PracticeLocation();
        location.setId(java.util.UUID.randomUUID());
        location.setStreet("Rue de Rivoli");
        location.setCity("Paris");

        when(repository.findTop50ByGeocodingAttemptedFalse()).thenReturn(List.of(location));

        // Simulation d'une réponse JSON valide de la BAN
        String fakeBanResponse = """
            {
                "features": [
                    {
                        "geometry": {
                            "coordinates": [2.3488, 48.8534]
                        }
                    }
                ]
            }
            """;
        when(restTemplate.getForObject(anyString(), eq(String.class), anyString()))
                .thenReturn(fakeBanResponse);

        // WHEN
        int processedCount = geocodingService.processGeocodingBatch();

        // THEN
        assertEquals(1, processedCount);
        assertTrue(location.isGeocodingAttempted());
        assertEquals(2.3488, location.getLongitude());
        assertEquals(48.8534, location.getLatitude());
        verify(repository).saveAll(any());
    }

    @Test
    void processGeocodingBatch_ApiError_ShouldCatchExceptionAndSaveAttempt() {
        // GIVEN
        PracticeLocation location = new PracticeLocation();
        location.setId(java.util.UUID.randomUUID());
        location.setCity("Lyon");

        when(repository.findTop50ByGeocodingAttemptedFalse()).thenReturn(List.of(location));
        // Simulation d'une erreur réseau
        when(restTemplate.getForObject(anyString(), eq(String.class), anyString()))
                .thenThrow(new RuntimeException("API BAN en panne"));

        // WHEN
        int processedCount = geocodingService.processGeocodingBatch();

        // THEN
        assertEquals(1, processedCount);
        assertTrue(location.isGeocodingAttempted());
        assertNull(location.getLongitude());
        verify(repository).saveAll(any());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }
}
