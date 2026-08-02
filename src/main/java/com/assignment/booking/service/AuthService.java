package com.assignment.booking.service;

import com.assignment.booking.dto.AuthResponse;
import com.assignment.booking.dto.LoginRequest;
import com.assignment.booking.dto.RegisterRequest;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
}
