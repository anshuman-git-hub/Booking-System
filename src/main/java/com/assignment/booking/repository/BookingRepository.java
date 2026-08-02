package com.assignment.booking.repository;

import com.assignment.booking.entity.Booking;
import com.assignment.booking.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findBySlotIdAndStatus(Long slotId, BookingStatus status);
}
