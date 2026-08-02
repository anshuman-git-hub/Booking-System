package com.assignment.booking.service;

import com.assignment.booking.dto.BookingRequest;
import com.assignment.booking.dto.BookingResponse;

public interface BookingService {
    BookingResponse bookSlot(BookingRequest request, String username);
    BookingResponse cancelOwnBooking(Long bookingId, String username);
    BookingResponse cancelAnyBooking(Long bookingId);
}
