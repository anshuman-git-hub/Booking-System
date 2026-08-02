package com.assignment.booking.integration;

import com.assignment.booking.dto.LoginRequest;
import com.assignment.booking.dto.RegisterRequest;
import com.assignment.booking.enums.Role;
import com.assignment.booking.repository.BookingRepository;
import com.assignment.booking.repository.SlotRepository;
import com.assignment.booking.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end check (real Spring Security filter chain, real JWT tokens,
 * real H2) that the SECURITY RULES from the spec are actually enforced:
 * - unauthenticated requests are rejected
 * - USER cannot create slots or hit admin endpoints
 * - ADMIN can create slots
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityRoleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private SlotRepository slotRepository;

    @BeforeEach
    void cleanDb() {
        bookingRepository.deleteAll();
        slotRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String registerAndLogin(String username, Role role) throws Exception {
        RegisterRequest register = new RegisterRequest(username, "password123", role);
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest(username, "password123");
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("token").asText();
    }

    @Test
    void getSlots_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/slots"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/slots"));
    }

    @Test
    void createSlot_asUser_isForbidden() throws Exception {
        String token = registerAndLogin("regular-user", Role.USER);
        String payload = "{\"startTime\":\"2099-01-01T10:00:00\",\"endTime\":\"2099-01-01T11:00:00\"}";

        mockMvc.perform(post("/slots")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.path").value("/slots"));
    }

    @Test
    void createSlot_asAdmin_isCreated() throws Exception {
        String token = registerAndLogin("system-admin", Role.ADMIN);
        String payload = "{\"startTime\":\"2099-01-01T10:00:00\",\"endTime\":\"2099-01-01T11:00:00\"}";

        mockMvc.perform(post("/slots")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void adminCancelEndpoint_asUser_isForbidden() throws Exception {
        String token = registerAndLogin("another-user", Role.USER);

        mockMvc.perform(post("/admin/bookings/1/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.path").value("/admin/bookings/1/cancel"));
    }

    @Test
    void getSlots_withValidUserToken_isOk() throws Exception {
        String token = registerAndLogin("browsing-user", Role.USER);

        mockMvc.perform(get("/slots")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void register_withDuplicateUsername_returnsConflict() throws Exception {
        registerAndLogin("dup-user", Role.USER);
        RegisterRequest again = new RegisterRequest("dup-user", "password123", Role.USER);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(again)))
                .andExpect(status().isConflict());
    }

    @Test
    void login_withWrongPassword_returnsUnauthorized() throws Exception {
        registerAndLogin("wrong-pass-user", Role.USER);
        LoginRequest badLogin = new LoginRequest("wrong-pass-user", "totallyWrongPassword");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badLogin)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/auth/login"));
    }
}
