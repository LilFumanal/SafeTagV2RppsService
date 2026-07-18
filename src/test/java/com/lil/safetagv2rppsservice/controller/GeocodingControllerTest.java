package com.lil.safetagv2rppsservice.controller;

import com.lil.safetagv2rppsservice.service.GeocodingService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.security.test.context.support.WithMockUser; // AJOUT
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(GeocodingController.class)
@WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
class GeocodingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GeocodingService geocodingService;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @Test
    void testTriggerGeocoding() throws Exception {
        // Préparation du comportement attendu
        when(geocodingService.processGeocodingBatch()).thenReturn(10);

        // Appel HTTP simulé et vérifications
        mockMvc.perform(post("/api/v1/geocoding/trigger").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string("Batch de géocodage terminé. 10 adresses traitées."));
    }
}
