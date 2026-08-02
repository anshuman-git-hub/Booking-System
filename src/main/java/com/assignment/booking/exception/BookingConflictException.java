package com.assignment.booking.exception;

/**
 * Thrown when a booking cannot be completed because the slot is no longer
 * available - either because it was already booked, or because a concurrent
 * transaction won the optimistic-locking race.
 */
public class BookingConflictException extends RuntimeException {
    public BookingConflictException(String message) {
        super(message);
    }
}
