package com.lil.safetagv2rppsservice.controller;

import com.lil.safetagv2rppsservice.dto.PractitionerDTO;
import com.lil.safetagv2rppsservice.dto.ProfessionDTO;
import com.lil.safetagv2rppsservice.service.PractitionerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser; // AJOUT : Pour bypasser la sécurité
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PractitionerController.class)
@WithMockUser
class PractitionerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PractitionerService practitionerService;

    // --- Tests pour searchPractitioners ---

    @Test
    @DisplayName("Doit retourner une page de praticiens lors d'une recherche avec des paramètres valides")
    void searchPractitioners_withValidParams_returnsPageOfPractitioners() throws Exception {
        // Given
        String name = "Jean Dupont";
        String professionCode = "10";
        String city = "Paris";
        Double lat = 48.8566;
        Double lon = 2.3522;
        Double radiusKm = 10.0;
        Pageable pageable = PageRequest.of(0, 10);

        PractitionerDTO practitioner1 = new PractitionerDTO(
                "12345678910", "Jean Dupont", List.of("10"), List.of("SM34"), List.of(), "Libéral");
        Page<PractitionerDTO> page = new PageImpl<>(List.of(practitioner1), pageable, 1);

        // Correction du stubbing pour s'assurer de correspondre exactement aux arguments envoyés
        when(practitionerService.searchPractitioners(
                eq(name), eq(professionCode), isNull(), eq(city), eq(lat), eq(lon), eq(radiusKm), eq(pageable)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/v1/practitioners/search")
                        .param("name", name)
                        .param("professionCode", professionCode)
                        .param("city", city)
                        .param("lat", String.valueOf(lat))
                        .param("lon", String.valueOf(lon))
                        .param("radiusKm", String.valueOf(radiusKm))
                        .param("page", String.valueOf(pageable.getPageNumber()))
                        .param("size", String.valueOf(pageable.getPageSize()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content[0].rppsId").value("12345678910"))
                .andExpect(jsonPath("$.content[0].name").value("Jean Dupont"))
                .andExpect(jsonPath("$.pageable.pageNumber").value(pageable.getPageNumber()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("Doit retourner une page vide lors d'une recherche sans résultats")
    void searchPractitioners_noResults_returnsEmptyPage() throws Exception {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<PractitionerDTO> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(practitionerService.searchPractitioners(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(pageable)))
                .thenReturn(emptyPage);

        // When & Then
        mockMvc.perform(get("/api/v1/practitioners/search")
                        .param("page", String.valueOf(pageable.getPageNumber()))
                        .param("size", String.valueOf(pageable.getPageSize()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // --- Tests pour getPractitioner ---

    @Test
    @DisplayName("Doit retourner les détails d'un praticien existant par ID RPPS")
    void getPractitioner_withExistingRppsId_returnsPractitionerDTO() throws Exception {
        // Given
        String rppsId = "11111111111";
        PractitionerDTO practitioner1 = new PractitionerDTO(
                rppsId, "Jean Dupont", List.of("10"), List.of("SM34"), List.of(), "Libéral");

        when(practitionerService.getAndUpdatePractitioner(rppsId)).thenReturn(practitioner1);

        // When & Then
        mockMvc.perform(get("/api/v1/practitioners/{rppsId}", rppsId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.rppsId").value(rppsId))
                .andExpect(jsonPath("$.name").value("Jean Dupont"));
    }

    @Test
    @DisplayName("Doit retourner 404 Not Found si le praticien n'existe pas")
    void getPractitioner_withNonExistingRppsId_returnsNotFound() throws Exception {
        // Given
        String rppsId = "99999999999";

        when(practitionerService.getAndUpdatePractitioner(rppsId)).thenReturn(null);

        // When & Then
        mockMvc.perform(get("/api/v1/practitioners/{rppsId}", rppsId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // --- NOUVEAU : Test pour getProfessions ---

    @Test
    @DisplayName("Doit retourner la liste des professions référencées")
    void getProfessions_returnsListOfProfessions() throws Exception {
        // Given
        ProfessionDTO profession1 = new ProfessionDTO("10", "Médecin");
        ProfessionDTO profession2 = new ProfessionDTO("26", "Audioprothésiste");
        List<ProfessionDTO> professionsList = List.of(profession1, profession2);

        when(practitionerService.getAllProfessions()).thenReturn(professionsList);

        // When & Then
        mockMvc.perform(get("/api/v1/practitioners/references/professions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].code").value("10"))
                .andExpect(jsonPath("$[0].label").value("Médecin"))
                .andExpect(jsonPath("$[1].code").value("26"))
                .andExpect(jsonPath("$[1].label").value("Audioprothésiste"));
    }
}