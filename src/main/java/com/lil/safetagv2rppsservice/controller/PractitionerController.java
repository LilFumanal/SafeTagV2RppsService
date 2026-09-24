package com.lil.safetagv2rppsservice.controller;

import com.lil.safetagv2rppsservice.dto.PractitionerDTO;
import com.lil.safetagv2rppsservice.dto.ProfessionDTO;
import com.lil.safetagv2rppsservice.service.PractitionerService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rpps")
public class PractitionerController {

    private final PractitionerService practitionerService;

    public PractitionerController(PractitionerService practitionerService) {
        this.practitionerService = practitionerService;
    }

    @GetMapping("/search")
    public ResponseEntity<Page<PractitionerDTO>> searchPractitioners(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String professionCode,
            @RequestParam(required = false) String specialtyCode,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon,
            @RequestParam(required = false) Double radiusKm,
            Pageable pageable) {

        return ResponseEntity.ok(practitionerService.searchPractitioners(
                name, professionCode, specialtyCode, city, lat, lon, radiusKm, pageable));
    }

    // 2. Consultation unitaire : Base locale + Mise à jour API e-santé (Opportuniste)
    @GetMapping("/{rppsId:\\d+}")
    public ResponseEntity<PractitionerDTO> getPractitioner(@PathVariable String rppsId) {
        PractitionerDTO practitioner = practitionerService.getAndUpdatePractitioner(rppsId);
        if (practitioner == null) {
            // Si le service retourne null, on renvoie 404
            return ResponseEntity.notFound().build();
        }
        // Sinon, on renvoie 200 OK avec le praticien
        return ResponseEntity.ok(practitioner);
    }

    // Dans ton RppsController
    @GetMapping("references/professions")
    public ResponseEntity<List<ProfessionDTO>> getProfessions() {
        List<ProfessionDTO> professions = practitionerService.getAllProfessions();
        System.out.println("DEBUG - Professions trouvées : " + professions.size() + " -> " + professions);
        return ResponseEntity.ok(professions);
    }

}