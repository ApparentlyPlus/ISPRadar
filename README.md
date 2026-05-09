# ISPRadar

ISPRadar is an ISP comparison app, made as a part of a university assignment. The frontend asks for the user to enter an address, calls the Spring Boot backend, and returns all ISP plans available, from all providers. It also highlights a best value for money (VFM) pick and lets users compare plans side-by-side.

## What The UI Asks For
The UI always asks for the superset of fields so all providers can be queried:
1. Nomos (dropdown from `/api/address/states`)
2. Dimos (dropdown from `/api/address/municipalities`)
3. Postal code (free text, used by Vodafone)
4. Street (dropdown from `/api/address/streets`)
5. Number (free text)
6. Area (dropdown from `/api/address/areas`, used by Cosmote)

Notes:
- Vodafone needs postal code and number, but not area.
- Cosmote needs area and number, but not postal code.
- The backend merges and de-duplicates options from both providers.

## Backend API
Base URL: `http://localhost:8080/api/address`

Endpoints:
- `GET /states`
- `POST /municipalities`
- `POST /postalcodes`
- `POST /streets`
- `POST /areas`
- `POST /numbers`
- `POST /check`

## Plan Normalization
- Vodafone plans come from `availableSpeeds` in their JSON API.
- Cosmote plans are parsed from HTML and normalized to plan codes like `FIBER_300` or `ADSL_24`.
- Cosmote speeds are derived:
  - $DL \in [0.80, 0.90] \times$ max speed
  - $UL = 0.70 \times (DL / 2)$

## Local Development
### Backend
Run from VS Code or with Maven:
```
cd backend
./mvnw spring-boot:run
```

### Frontend
```
cd frontend
npm install
npm run dev
```
