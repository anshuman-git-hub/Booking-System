package com.assignment.booking.service;

import com.assignment.booking.dto.BookingRequest;
import com.assignment.booking.dto.BookingResponse;
import com.assignment.booking.entity.*;
import com.assignment.booking.enums.*;
import com.assignment.booking.exception.BookingConflictException;
import com.assignment.booking.exception.ResourceNotFoundException;
import com.assignment.booking.exception.UnauthorizedActionException;
import com.assignment.booking.repository.BookingRepository;
import com.assignment.booking.repository.SlotRepository;
import com.assignment.booking.repository.UserRepository;
import com.assignment.booking.service.impl.BookingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private SlotRepository slotRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BookingServiceImpl bookingService;

    private Slot availableSlot;
    private User user;

    @BeforeEach
    void setUp() {
        availableSlot = Slot.builder()
                .id(1L)
                .startTime(LocalDateTime.now().plusHours(1))
                .endTime(LocalDateTime.now().plusHours(2))
                .status(SlotStatus.AVAILABLE)
                .version(0L)
                .build();

        user = User.builder()
                .id(10L)
                .username("alice")
                .password("hashed")
                .role(Role.USER)
                .build();
    }

    @Test
    void bookSlot_succeedsWhenSlotAvailable() {
        BookingRequest request = new BookingRequest(1L);
        when(slotRepository.findById(1L)).thenReturn(Optional.of(availableSlot));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(slotRepository.save(any(Slot.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(100L);
            return b;
        });

        BookingResponse response = bookingService.bookSlot(request, "alice");

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getSlotId()).isEqualTo(1L);
        assertThat(response.getUsername()).isEqualTo("alice");
        assertThat(response.getStatus()).isEqualTo(BookingStatus.ACTIVE);
        assertThat(availableSlot.getStatus()).isEqualTo(SlotStatus.BOOKED);

        verify(slotRepository).save(availableSlot);
        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    void bookSlot_throwsConflictWhenAlreadyBooked() {
        availableSlot.setStatus(SlotStatus.BOOKED);
        BookingRequest request = new BookingRequest(1L);
        when(slotRepository.findById(1L)).thenReturn(Optional.of(availableSlot));

        assertThatThrownBy(() -> bookingService.bookSlot(request, "alice"))
                .isInstanceOf(BookingConflictException.class)
                .hasMessageContaining("already booked");

        verify(bookingRepository, never()).save(any());
        verify(slotRepository, never()).save(any());
    }

    @Test
    void bookSlot_throwsNotFoundWhenSlotMissing() {
        BookingRequest request = new BookingRequest(99L);
        when(slotRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.bookSlot(request, "alice"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void bookSlot_throwsNotFoundWhenUserMissing() {
        BookingRequest request = new BookingRequest(1L);
        when(slotRepository.findById(1L)).thenReturn(Optional.of(availableSlot));
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.bookSlot(request, "ghost"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void cancelOwnBooking_succeedsForOwner() {
        Booking booking = Booking.builder()
                .id(5L).slot(availableSlot).user(user).status(BookingStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).build();
        availableSlot.setStatus(SlotStatus.BOOKED);

        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(slotRepository.save(any(Slot.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse response = bookingService.cancelOwnBooking(5L, "alice");

        assertThat(response.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(availableSlot.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
    }

    @Test
    void cancelOwnBooking_throwsUnauthorizedForNonOwner() {
        Booking booking = Booking.builder()
                .id(5L).slot(availableSlot).user(user).status(BookingStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).build();
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelOwnBooking(5L, "mallory"))
                .isInstanceOf(UnauthorizedActionException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void cancelOwnBooking_throwsConflictWhenAlreadyCancelled() {
        Booking booking = Booking.builder()
                .id(5L).slot(availableSlot).user(user).status(BookingStatus.CANCELLED)
                .createdAt(LocalDateTime.now()).build();
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelOwnBooking(5L, "alice"))
                .isInstanceOf(BookingConflictException.class);
    }

    @Test
    void cancelOwnBooking_throwsNotFoundWhenBookingMissing() {
        when(bookingRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.cancelOwnBooking(404L, "alice"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void cancelAnyBooking_succeedsRegardlessOfOwner() {
        Booking booking = Booking.builder()
                .id(5L).slot(availableSlot).user(user).status(BookingStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).build();
        availableSlot.setStatus(SlotStatus.BOOKED);

        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(slotRepository.save(any(Slot.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse response = bookingService.cancelAnyBooking(5L);

        assertThat(response.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(availableSlot.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
    }

    @Test
    void cancelAnyBooking_throwsNotFoundWhenMissing() {
        when(bookingRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.cancelAnyBooking(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
