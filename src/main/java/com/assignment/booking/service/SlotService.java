package com.assignment.booking.service;

import com.assignment.booking.dto.SlotRequest;
import com.assignment.booking.dto.SlotResponse;

import java.util.List;

public interface SlotService {
    SlotResponse createSlot(SlotRequest request);
    List<SlotResponse> getAllSlots();
}
