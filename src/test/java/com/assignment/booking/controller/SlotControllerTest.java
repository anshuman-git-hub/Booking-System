package com.assignment.booking.controller;

import com.assignment.booking.dto.SlotRequest;
import com.assignment.booking.dto.SlotResponse;
import com.assignment.booking.enums.SlotStatus;
import com.assignment.booking.service.SlotService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SlotController.class)
@org.springframework.context.annotation.Import(com.assignment.booking.config.SecurityConfig.class)
@AutoConfigureMockMvc
class SlotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SlotService slotService;

    @MockBean
    private com.assignment.booking.security.JwtService jwtService;

    @MockBean
    private com.assignment.booking.security.UserDetailsServiceImpl userDetailsService;


    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "admin", roles = "ADMIN")
    void createSlot_returns201WithValidPayload() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(2);
        SlotRequest request = new SlotRequest(start, end);
        SlotResponse response = SlotResponse.builder()
                .id(1L).startTime(start).endTime(end).status(SlotStatus.AVAILABLE).build();

        when(slotService.createSlot(any())).thenReturn(response);

        mockMvc.perform(post("/slots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "admin", roles = "ADMIN")
    void createSlot_returns400WhenStartTimeMissing() throws Exception {
        String payload = "{\"endTime\":\"2099-01-01T10:00:00\"}";

        mockMvc.perform(post("/slots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "admin", roles = "ADMIN")
    void createSlot_returns400WhenStartTimeInPast() throws Exception {
        String payload = "{\"startTime\":\"2020-01-01T10:00:00\",\"endTime\":\"2020-01-01T11:00:00\"}";

        mockMvc.perform(post("/slots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "alice", roles = "USER")
    void getAllSlots_returns200WithList() throws Exception {
        SlotResponse slot = SlotResponse.builder()
                .id(1L).startTime(LocalDateTime.now().plusHours(1))
                .endTime(LocalDateTime.now().plusHours(2)).status(SlotStatus.AVAILABLE).build();
        when(slotService.getAllSlots()).thenReturn(List.of(slot));

        mockMvc.perform(get("/slots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }
}
