package com.assignment.booking.service;

import com.assignment.booking.dto.AuthResponse;
import com.assignment.booking.dto.LoginRequest;
import com.assignment.booking.dto.RegisterRequest;
import com.assignment.booking.enums.Role;
import com.assignment.booking.entity.User;
import com.assignment.booking.exception.DuplicateUsernameException;
import com.assignment.booking.repository.UserRepository;
import com.assignment.booking.security.JwtService;
import com.assignment.booking.security.UserDetailsServiceImpl;
import com.assignment.booking.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserDetailsServiceImpl userDetailsService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void register_succeedsForNewUsername() {
        RegisterRequest request = new RegisterRequest("alice", "password123", Role.USER);
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername("alice").password("hashed").authorities("ROLE_USER").build();
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails);
        when(jwtService.generateToken(userDetails)).thenReturn("mock-jwt-token");

        AuthResponse response = authService.register(request);

        assertThat(response.getToken()).isEqualTo("mock-jwt-token");
        assertThat(response.getUsername()).isEqualTo("alice");
        assertThat(response.getRole()).isEqualTo("USER");
    }

    @Test
    void register_throwsWhenUsernameTaken() {
        RegisterRequest request = new RegisterRequest("alice", "password123", Role.USER);
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateUsernameException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_succeedsWithValidCredentials() {
        LoginRequest request = new LoginRequest("alice", "password123");
        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername("alice").password("hashed")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))).build();

        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails);
        when(jwtService.generateToken(userDetails)).thenReturn("mock-jwt-token");

        AuthResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("mock-jwt-token");
        assertThat(response.getUsername()).isEqualTo("alice");
        assertThat(response.getRole()).isEqualTo("ADMIN");
        verify(authenticationManager).authenticate(any());
    }
}
