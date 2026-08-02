package com.assignment.booking.service.impl;

import com.assignment.booking.dto.SlotRequest;
import com.assignment.booking.dto.SlotResponse;
import com.assignment.booking.entity.Slot;
import com.assignment.booking.enums.SlotStatus;
import com.assignment.booking.repository.SlotRepository;
import com.assignment.booking.service.SlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SlotServiceImpl implements SlotService {

    private final SlotRepository slotRepository;

    @Override
    @Transactional
    public SlotResponse createSlot(SlotRequest request) {
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }

        Slot slot = Slot.builder()
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(SlotStatus.AVAILABLE)
                .build();

        Slot saved = slotRepository.save(slot);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SlotResponse> getAllSlots() {
        return slotRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    private SlotResponse toResponse(Slot slot) {
        return SlotResponse.builder()
                .id(slot.getId())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .status(slot.getStatus())
                .build();
    }
}
