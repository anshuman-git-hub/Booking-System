package com.assignment.booking.controller;

import com.assignment.booking.dto.BookingRequest;
import com.assignment.booking.dto.BookingResponse;
import com.assignment.booking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    /** Role: USER — enforced in SecurityConfig. */
    @PostMapping
    public ResponseEntity<BookingResponse> bookSlot(@Valid @RequestBody BookingRequest request,
                                                      Authentication authentication) {
        BookingResponse response = bookingService.bookSlot(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Role: USER — a user may only cancel their own booking. */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<BookingResponse> cancelOwnBooking(@PathVariable("id") Long id,
                                                              Authentication authentication) {
        return ResponseEntity.ok(bookingService.cancelOwnBooking(id, authentication.getName()));
    }
}
