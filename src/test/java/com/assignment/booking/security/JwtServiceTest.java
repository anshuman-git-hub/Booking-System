package com.assignment.booking.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String TEST_SECRET =
            "c2VjcmV0LWtleS1mb3ItYm9va2luZy1zeXN0ZW0tYXNzaWdubWVudC1kZW1vLTEyMzQ1Njc4OTA=";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, 3600000L);
    }

    @Test
    void generateToken_andExtractUsername_roundTrips() {
        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername("alice").password("hashed").authorities("ROLE_USER").build();

        String token = jwtService.generateToken(userDetails);

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("alice");
    }

    @Test
    void isTokenValid_returnsTrueForMatchingUser() {
        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername("bob").password("hashed").authorities("ROLE_ADMIN").build();

        String token = jwtService.generateToken(userDetails);

        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
    }

    @Test
    void isTokenValid_returnsFalseForDifferentUser() {
        UserDetails alice = org.springframework.security.core.userdetails.User
                .withUsername("alice").password("hashed").authorities("ROLE_USER").build();
        UserDetails mallory = org.springframework.security.core.userdetails.User
                .withUsername("mallory").password("hashed").authorities("ROLE_USER").build();

        String token = jwtService.generateToken(alice);

        assertThat(jwtService.isTokenValid(token, mallory)).isFalse();
    }

    @Test
    void expiredToken_isNotValid() throws InterruptedException {
        JwtService shortLivedJwtService = new JwtService(TEST_SECRET, 1L); // 1ms expiry
        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername("alice").password("hashed").authorities("ROLE_USER").build();

        String token = shortLivedJwtService.generateToken(userDetails);
        Thread.sleep(10);

        assertThat(shortLivedJwtService.isTokenValid(token, userDetails)).isFalse();
    }
}
