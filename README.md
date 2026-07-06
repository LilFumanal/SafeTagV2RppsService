# SafeTag - RPPS Service

Microservice responsable de la gestion, centralisation et consultation des données des professionnels de santé (RPPS). 

Ce service agit comme la source de vérité pour les praticiens au sein de l'écosystème SafeTag. Il est conçu pour être résilient face à l'instabilité de l'API e-santé.gouv v2.0.

## 🚀 Fonctionnalités principales
* **Import CSV** : Alimentation initiale de la base de données locale (PostgreSQL) via les extractions officielles.
* **Client API Résilient** : Interrogation de l'API e-santé avec mécanismes de tolérance aux pannes (Retry, Fallback).
* **Consultation Opportuniste** : Recherche locale prioritaire et mise à jour à la volée depuis l'API e-santé en cas de donnée manquante ou obsolète.
* **Recherche Multicritères** : API de recherche spatiale et filtrée, optimisée sur la base locale.

## 🛠️ Stack Technique
* **Java / Spring Boot**
* **PostgreSQL** (Base de données relationnelle locale)
* **Spring Data JPA** 

---

## 📖 Documentation de l'API (V1)

Toutes les requêtes s'effectuent sur le port par défaut (ex: `http://localhost:8080`).

### 1. Recherche multicritères
Recherche dans la base locale uniquement. Paginée.

- **Méthode** : `GET`
- **Chemin** : `/practitioners/search`
- **Paramètres optionnels** : `name`, `professionCode`, `specialtyCode`, `city`, `lat`, `lon`, `radiusKm`, `page`, `size`

**Exemple d'appel :**
```bash
curl -X GET "http://localhost:8080/practitioners/search?city=Paris&specialtyCode=SM54&page=0&size=20" 
```
### 2. Consultation unitaire
Récupère les détails d'un praticien (ID RPPS à 11 chiffres). Utilise l'API e-santé en fallback si absent ou obsolète en base locale.

Méthode : GET
Chemin : /practitioners/{rppsId}

Exemple d'appel :
```bash
curl -X GET "http://localhost:8080/practitioners/810000000000"
```
(Retourne 200 OK avec le DTO du praticien, ou 404 Not Found si totalement introuvable).

### 3. Payload JSON
```JSON
{
  "rppsId": "",
  "name": "",
  "professionCode": "",
  "specialtyCode": "",
  "exerciceMode": "",
  "locations": [
    {
      "facilityName": "",
      "streetNumber": "",
      "street": "",
      "zipCode": "",
      "city": "",
      "latitude": null,
      "longitude": null
    }
  ]
}

```
⚙️ Installation & Lancement

S'assurer qu'une instance PostgreSQL est en cours d'exécution avec les identifiants configurés dans application.properties (ou .yml).
Lancer l'application :

mvn spring-boot:run

---
