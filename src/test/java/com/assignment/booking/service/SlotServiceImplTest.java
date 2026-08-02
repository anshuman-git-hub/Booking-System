package com.assignment.booking.service;

import com.assignment.booking.dto.SlotRequest;
import com.assignment.booking.dto.SlotResponse;
import com.assignment.booking.entity.Slot;
import com.assignment.booking.enums.SlotStatus;
import com.assignment.booking.repository.SlotRepository;
import com.assignment.booking.service.impl.SlotServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlotServiceImplTest {

    @Mock
    private SlotRepository slotRepository;

    @InjectMocks
    private SlotServiceImpl slotService;

    @Test
    void createSlot_succeedsWithValidTimes() {
        LocalDateTime start = LocalDateTime.now().plusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(2);
        SlotRequest request = new SlotRequest(start, end);

        when(slotRepository.save(any(Slot.class))).thenAnswer(inv -> {
            Slot s = inv.getArgument(0);
            s.setId(1L);
            s.setVersion(0L);
            return s;
        });

        SlotResponse response = slotService.createSlot(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(response.getStartTime()).isEqualTo(start);
        assertThat(response.getEndTime()).isEqualTo(end);
    }

    @Test
    void createSlot_throwsWhenEndBeforeStart() {
        LocalDateTime start = LocalDateTime.now().plusHours(2);
        LocalDateTime end = LocalDateTime.now().plusHours(1);
        SlotRequest request = new SlotRequest(start, end);

        assertThatThrownBy(() -> slotService.createSlot(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endTime must be after startTime");

        verify(slotRepository, never()).save(any());
    }

    @Test
    void createSlot_throwsWhenEndEqualsStart() {
        LocalDateTime start = LocalDateTime.now().plusHours(1);
        SlotRequest request = new SlotRequest(start, start);

        assertThatThrownBy(() -> slotService.createSlot(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getAllSlots_returnsMappedList() {
        Slot slot1 = Slot.builder().id(1L).startTime(LocalDateTime.now().plusHours(1))
                .endTime(LocalDateTime.now().plusHours(2)).status(SlotStatus.AVAILABLE).version(0L).build();
        Slot slot2 = Slot.builder().id(2L).startTime(LocalDateTime.now().plusHours(3))
                .endTime(LocalDateTime.now().plusHours(4)).status(SlotStatus.BOOKED).version(1L).build();

        when(slotRepository.findAll()).thenReturn(List.of(slot1, slot2));

        List<SlotResponse> result = slotService.getAllSlots();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(1).getStatus()).isEqualTo(SlotStatus.BOOKED);
    }

    @Test
    void getAllSlots_returnsEmptyListWhenNoSlots() {
        when(slotRepository.findAll()).thenReturn(List.of());

        List<SlotResponse> result = slotService.getAllSlots();

        assertThat(result).isEmpty();
    }
}
