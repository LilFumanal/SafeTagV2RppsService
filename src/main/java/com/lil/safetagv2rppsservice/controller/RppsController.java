package com.lil.safetagv2rppsservice.controller;

import com.lil.safetagv2rppsservice.service.RppsIngestionService;
import com.lil.safetagv2rppsservice.entity.RppsPractitioner;
import com.lil.safetagv2rppsservice.repository.RppsPractitionerRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rpps")
@RequiredArgsConstructor
public class RppsController {

    private final RppsIngestionService ingestionService;
    private final RppsPractitionerRepository repository;

    @PostConstruct
    public void init() {
        System.out.println("=================================================");
        System.out.println("🔥 RPPS CONTROLLER BIEN INITIALISÉ PAR SPRING ! 🔥");
        System.out.println("=================================================");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/import")
    public ResponseEntity<String> triggerImport(
            @RequestHeader HttpHeaders allHeaders
            ) {
        System.out.println("=== 🎯 RPPS : REQUÊTE REÇUE ===");

        allHeaders.forEach((key, value) -> {
            System.out.println(String.format("Header reçu -> %s : %s", key, value));
        });
        System.out.println("===============================");

        System.out.println("[INFO] Requête reçue : Lancement de l'importation RPPS...");

        try {
            ingestionService.importRppsData();
            return ResponseEntity.ok("Importation réussie.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Erreur : " + e.getMessage());
        }
    }
    @GetMapping("/{rppsId}")
    public ResponseEntity<RppsPractitioner> getPractitionerById(@PathVariable String rppsId) {
        return repository.findByRppsId(rppsId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}