package com.assignment.booking.service.impl;

import com.assignment.booking.dto.BookingRequest;
import com.assignment.booking.dto.BookingResponse;
import com.assignment.booking.entity.Booking;
import com.assignment.booking.enums.BookingStatus;
import com.assignment.booking.entity.Slot;
import com.assignment.booking.enums.SlotStatus;
import com.assignment.booking.entity.User;
import com.assignment.booking.exception.BookingConflictException;
import com.assignment.booking.exception.ResourceNotFoundException;
import com.assignment.booking.exception.UnauthorizedActionException;
import com.assignment.booking.repository.BookingRepository;
import com.assignment.booking.repository.SlotRepository;
import com.assignment.booking.repository.UserRepository;
import com.assignment.booking.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final SlotRepository slotRepository;
    private final UserRepository userRepository;

    /**
     * Race-condition-safe booking.
     * <p>
     * Flow:
     * 1. Load the slot (this reads its current @Version value into the
     *    persistence context).
     * 2. Fail fast if it is already BOOKED - avoids doing unnecessary work
     *    for the common case where the slot is genuinely taken.
     * 3. Flip status to BOOKED and let Hibernate flush the UPDATE at
     *    commit time: {@code UPDATE slot SET status=?, version=? WHERE
     *    id=? AND version=<version we read>}.
     * 4. If a concurrent transaction already booked (and committed) the
     *    same slot between step 1 and this flush, the WHERE clause matches
     *    zero rows and Hibernate throws
     *    {@link org.springframework.orm.ObjectOptimisticLockingFailureException}.
     *    Because the whole method is @Transactional, that failure rolls
     *    back this transaction entirely - no Booking row is inserted and
     *    the Slot update is discarded, so the two tables never go out of
     *    sync (partial updates cannot occur).
     * <p>
     * The exception is not caught here; it propagates to
     * {@code GlobalExceptionHandler}, which returns 409 Conflict with a
     * clear message. This is what guarantees "only one booking per slot"
     * without any in-memory locks/synchronized blocks, and it survives
     * application restarts because the lock lives in the database row
     * itself (the version column), not in JVM memory.
     */
    @Override
    @Transactional
    public BookingResponse bookSlot(BookingRequest request, String username) {
        Slot slot = slotRepository.findById(request.getSlotId())
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found: " + request.getSlotId()));

        if (slot.getStatus() == SlotStatus.BOOKED) {
            throw new BookingConflictException("Slot " + slot.getId() + " is already booked");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        slot.setStatus(SlotStatus.BOOKED);
        // save() triggers the version-checked UPDATE described above.
        slotRepository.save(slot);

        Booking booking = Booking.builder()
                .slot(slot)
                .user(user)
                .status(BookingStatus.ACTIVE)
                .build();
        Booking saved = bookingRepository.save(booking);

        log.info("Slot {} booked by user '{}' (bookingId={})", slot.getId(), username, saved.getId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public BookingResponse cancelOwnBooking(Long bookingId, String username) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        if (!booking.getUser().getUsername().equals(username)) {
            throw new UnauthorizedActionException("You can only cancel your own booking");
        }

        return cancel(booking);
    }

    @Override
    @Transactional
    public BookingResponse cancelAnyBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        return cancel(booking);
    }

    private BookingResponse cancel(Booking booking) {
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BookingConflictException("Booking " + booking.getId() + " is already cancelled");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        Slot slot = booking.getSlot();
        slot.setStatus(SlotStatus.AVAILABLE);
        slotRepository.save(slot);

        return toResponse(booking);
    }

    private BookingResponse toResponse(Booking booking) {
        return BookingResponse.builder()
                .id(booking.getId())
                .slotId(booking.getSlot().getId())
                .username(booking.getUser().getUsername())
                .status(booking.getStatus())
                .createdAt(booking.getCreatedAt())
                .build();
    }
}
