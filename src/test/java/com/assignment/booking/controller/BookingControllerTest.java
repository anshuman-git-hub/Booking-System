package com.assignment.booking.controller;

import com.assignment.booking.dto.BookingRequest;
import com.assignment.booking.dto.BookingResponse;
import com.assignment.booking.enums.BookingStatus;
import com.assignment.booking.exception.BookingConflictException;
import com.assignment.booking.exception.UnauthorizedActionException;
import com.assignment.booking.service.BookingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {BookingController.class, AdminBookingController.class})
@org.springframework.context.annotation.Import(com.assignment.booking.config.SecurityConfig.class)
@AutoConfigureMockMvc
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookingService bookingService;

    @MockBean
    private com.assignment.booking.security.JwtService jwtService;

    @MockBean
    private com.assignment.booking.security.UserDetailsServiceImpl userDetailsService;


    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void bookSlot_returns201WhenSuccessful() throws Exception {
        BookingRequest request = new BookingRequest(1L);
        BookingResponse response = BookingResponse.builder()
                .id(100L).slotId(1L).username("alice")
                .status(BookingStatus.ACTIVE).createdAt(LocalDateTime.now()).build();
        when(bookingService.bookSlot(any(), eq("alice"))).thenReturn(response);

        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void bookSlot_returns409WhenSlotAlreadyBooked() throws Exception {
        BookingRequest request = new BookingRequest(1L);
        when(bookingService.bookSlot(any(), eq("alice")))
                .thenThrow(new BookingConflictException("Slot 1 is already booked"));

        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Slot 1 is already booked"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void bookSlot_returns400WhenSlotIdMissing() throws Exception {
        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void cancelOwnBooking_returns200WhenSuccessful() throws Exception {
        BookingResponse response = BookingResponse.builder()
                .id(100L).slotId(1L).username("alice")
                .status(BookingStatus.CANCELLED).createdAt(LocalDateTime.now()).build();
        when(bookingService.cancelOwnBooking(eq(100L), eq("alice"))).thenReturn(response);

        mockMvc.perform(post("/bookings/100/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @WithMockUser(username = "mallory", roles = "USER")
    void cancelOwnBooking_returns403WhenNotOwner() throws Exception {
        when(bookingService.cancelOwnBooking(eq(100L), eq("mallory")))
                .thenThrow(new UnauthorizedActionException("You can only cancel your own booking"));

        mockMvc.perform(post("/bookings/100/cancel"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void cancelAnyBooking_returns200ForAdmin() throws Exception {
        BookingResponse response = BookingResponse.builder()
                .id(100L).slotId(1L).username("alice")
                .status(BookingStatus.CANCELLED).createdAt(LocalDateTime.now()).build();
        when(bookingService.cancelAnyBooking(100L)).thenReturn(response);

        mockMvc.perform(post("/admin/bookings/100/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
