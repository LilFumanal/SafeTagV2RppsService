package com.lil.safetagv2rppsservice.controller;

import com.lil.safetagv2rppsservice.service.RppsIngestionService;
import com.lil.safetagv2rppsservice.entity.RppsPractitioner;
import com.lil.safetagv2rppsservice.repository.RppsPractitionerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(RppsController.class)
@WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
class RppsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RppsIngestionService ingestionService;

    @MockBean
    private RppsPractitionerRepository repository;

    @Test
    void testTriggerImport_Success() throws Exception {
        doNothing().when(ingestionService).importRppsData();

        mockMvc.perform(post("/api/v1/rpps/import").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void testTriggerImport_Error() throws Exception {
        doThrow(new RuntimeException("Erreur simulée")).when(ingestionService).importRppsData();

        mockMvc.perform(post("/api/v1/rpps/import").with(csrf()))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void testGetPractitionerById_Found() throws Exception {
        RppsPractitioner mockPractitioner = new RppsPractitioner();
        mockPractitioner.setRppsId("12345678910");
        mockPractitioner.setName("Jean Dupont");

        when(repository.findByRppsId("12345678910")).thenReturn(Optional.of(mockPractitioner));

        mockMvc.perform(get("/api/v1/rpps/12345678910"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rppsId").value("12345678910"))
                .andExpect(jsonPath("$.name").value("Jean Dupont"));
    }

    @Test
    void testGetPractitionerById_NotFound() throws Exception {
        when(repository.findByRppsId(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/rpps/99999999999"))
                .andExpect(status().isNotFound());
    }
}
