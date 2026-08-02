package com.assignment.booking.controller;

import com.assignment.booking.dto.AuthResponse;
import com.assignment.booking.dto.LoginRequest;
import com.assignment.booking.dto.RegisterRequest;
import com.assignment.booking.enums.Role;
import com.assignment.booking.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@org.springframework.context.annotation.Import(com.assignment.booking.config.SecurityConfig.class)
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private com.assignment.booking.security.JwtService jwtService;

    @MockBean
    private com.assignment.booking.security.UserDetailsServiceImpl userDetailsService;

    @Test
    void register_returns201WithValidPayload() throws Exception {
        RegisterRequest request = new RegisterRequest("alice", "password123", Role.USER);
        AuthResponse response = AuthResponse.builder().token("jwt").username("alice").role("USER").build();
        when(authService.register(any())).thenReturn(response);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt"))
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void register_returns400WhenUsernameBlank() throws Exception {
        RegisterRequest request = new RegisterRequest("", "password123", Role.USER);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_returns400WhenPasswordTooShort() throws Exception {
        RegisterRequest request = new RegisterRequest("alice", "123", Role.USER);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_returns200WithValidCredentials() throws Exception {
        LoginRequest request = new LoginRequest("alice", "password123");
        AuthResponse response = AuthResponse.builder().token("jwt").username("alice").role("USER").build();
        when(authService.login(any())).thenReturn(response);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt"));
    }

    @Test
    void login_returns400WhenPayloadInvalid() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
