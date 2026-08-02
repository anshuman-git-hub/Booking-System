package com.assignment.booking;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BookingSystemApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the full Spring context (security, JPA, JWT beans, etc.)
        // wires up correctly.
    }
}
