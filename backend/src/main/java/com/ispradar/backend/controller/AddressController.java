package com.ispradar.backend.controller;

import com.ispradar.backend.dto.AddressItem;
import com.ispradar.backend.dto.AddressResponse;
import com.ispradar.backend.dto.AvailabilityRequest;
import com.ispradar.backend.dto.AvailabilityResponse;
import com.ispradar.backend.service.AddressOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/address")
public class AddressController {

    private final AddressOrchestrator orchestrator;

    public AddressController(AddressOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @GetMapping("/states")
    public ResponseEntity<AddressResponse> states() {
        return ResponseEntity.ok(orchestrator.getStates());
    }

    @PostMapping("/municipalities")
    public ResponseEntity<AddressResponse> municipalities(@RequestBody AddressItem state) {
        return ResponseEntity.ok(orchestrator.getMunicipalities(state));
    }

    @PostMapping("/postalcodes")
    public ResponseEntity<AddressResponse> postalCodes(@RequestBody Map<String, AddressItem> body) {
        return ResponseEntity.ok(
                orchestrator.getPostalCodes(body.get("state"), body.get("municipality")));
    }

    @PostMapping("/streets")
    public ResponseEntity<AddressResponse> streets(@RequestBody Map<String, AddressItem> body) {
        return ResponseEntity.ok(orchestrator.getStreets(
                body.get("state"), body.get("municipality"), body.get("postalCode")));
    }

    @PostMapping("/areas")
    public ResponseEntity<AddressResponse> areas(@RequestBody AddressItem street) {
        return ResponseEntity.ok(orchestrator.getAreas(street));
    }

    @PostMapping("/numbers")
    public ResponseEntity<AddressResponse> numbers(@RequestBody Map<String, AddressItem> body) {
        return ResponseEntity.ok(orchestrator.getNumbers(
                body.get("state"), body.get("municipality"),
                body.get("postalCode"), body.get("street")));
    }

    @PostMapping("/check")
    public ResponseEntity<AvailabilityResponse> check(@RequestBody AvailabilityRequest req) {
        return ResponseEntity.ok(orchestrator.checkAvailability(req));
    }
}