package com.assignment.booking.controller;

import com.assignment.booking.dto.BookingResponse;
import com.assignment.booking.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/bookings")
@RequiredArgsConstructor
public class AdminBookingController {

    private final BookingService bookingService;

    /** Role: ADMIN — can cancel any booking, enforced in SecurityConfig. */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<BookingResponse> cancelAnyBooking(@PathVariable("id") Long id) {
        return ResponseEntity.ok(bookingService.cancelAnyBooking(id));
    }
}
