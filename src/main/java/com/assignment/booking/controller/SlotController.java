package com.assignment.booking.controller;

import com.assignment.booking.dto.SlotRequest;
import com.assignment.booking.dto.SlotResponse;
import com.assignment.booking.service.SlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/slots")
@RequiredArgsConstructor
public class SlotController {

    private final SlotService slotService;

    /** Role: ADMIN — enforced in SecurityConfig. */
    @PostMapping
    public ResponseEntity<SlotResponse> createSlot(@Valid @RequestBody SlotRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(slotService.createSlot(request));
    }

    /** Role: USER, ADMIN — enforced in SecurityConfig. */
    @GetMapping
    public ResponseEntity<List<SlotResponse>> getAllSlots() {
        return ResponseEntity.ok(slotService.getAllSlots());
    }
}
