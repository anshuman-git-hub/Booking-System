package com.assignment.booking.dto;

import com.assignment.booking.enums.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {
    private Long id;
    private Long slotId;
    private String username;
    private BookingStatus status;
    private LocalDateTime createdAt;
}
