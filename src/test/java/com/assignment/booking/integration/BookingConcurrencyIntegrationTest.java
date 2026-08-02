package com.assignment.booking.integration;

import com.assignment.booking.dto.BookingRequest;
import com.assignment.booking.dto.BookingResponse;
import com.assignment.booking.entity.*;
import com.assignment.booking.enums.*;
import com.assignment.booking.repository.BookingRepository;
import com.assignment.booking.repository.SlotRepository;
import com.assignment.booking.repository.UserRepository;
import com.assignment.booking.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This is the test that proves the assignment's central requirement:
 * "the system must guarantee that a slot can be booked only once, even
 * under high concurrency."
 * <p>
 * N threads race to book the *same* slot at the *same* time using real
 * database transactions (H2, real connections, real optimistic-locking
 * version checks - no mocks). We assert that exactly one booking succeeds,
 * every other attempt fails with a conflict, and the database ends up in a
 * consistent state (slot BOOKED, exactly one ACTIVE booking row).
 */
@SpringBootTest
@ActiveProfiles("test")
class BookingConcurrencyIntegrationTest {

    private static final int CONCURRENT_USERS = 20;

    @Autowired
    private BookingService bookingService;
    @Autowired
    private SlotRepository slotRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long slotId;
    private List<String> usernames;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        slotRepository.deleteAll();
        userRepository.deleteAll();

        Slot slot = Slot.builder()
                .startTime(LocalDateTime.now().plusDays(1))
                .endTime(LocalDateTime.now().plusDays(1).plusHours(1))
                .status(SlotStatus.AVAILABLE)
                .build();
        slotId = slotRepository.save(slot).getId();

        usernames = generateUsernames(CONCURRENT_USERS);
        for (String username : usernames) {
            User user = User.builder()
                    .username(username)
                    .password(passwordEncoder.encode("password123"))
                    .role(Role.USER)
                    .build();
            userRepository.save(user);
        }
    }

    private List<String> generateUsernames(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> "concurrent-user-" + i)
                .toList();
    }

    @Test
    void onlyOneBookingSucceeds_whenManyUsersRaceForTheSameSlot() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_USERS);
        CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_USERS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_USERS);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger unexpectedErrorCount = new AtomicInteger(0);

        for (String username : usernames) {
            executor.submit(() -> {
                try {
                    readyLatch.countDown();
                    // All threads wait here so they hit the service at
                    // (as close to) the exact same moment as possible -
                    // this is what actually exercises the race condition
                    // instead of letting requests trickle in sequentially.
                    startLatch.await();

                    BookingResponse response = bookingService.bookSlot(new BookingRequest(slotId), username);
                    if (response != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ex) {
                    // Both our explicit BookingConflictException (fast path)
                    // and Hibernate's ObjectOptimisticLockingFailureException
                    // (the actual race being lost at commit time) land here.
                    conflictCount.incrementAndGet();
                    if (!isExpectedConflict(ex)) {
                        unexpectedErrorCount.incrementAndGet();
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // fire the starting gun - all threads proceed together
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).as("all booking attempts should complete within timeout").isTrue();
        assertThat(unexpectedErrorCount.get()).as("no unexpected exceptions").isZero();

        // The core guarantee: exactly one winner, everyone else conflicts.
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(CONCURRENT_USERS - 1);

        // And the database itself is left in a consistent state.
        Slot finalSlot = slotRepository.findById(slotId).orElseThrow();
        assertThat(finalSlot.getStatus()).isEqualTo(SlotStatus.BOOKED);

        long activeBookings = bookingRepository.findAll().stream()
                .filter(b -> b.getSlot().getId().equals(slotId))
                .filter(b -> b.getStatus() == BookingStatus.ACTIVE)
                .count();
        assertThat(activeBookings).isEqualTo(1);

        long totalBookingRows = bookingRepository.findAll().stream()
                .filter(b -> b.getSlot().getId().equals(slotId))
                .count();
        // No partial/orphaned booking rows were created for the losing attempts.
        assertThat(totalBookingRows).isEqualTo(1);
    }

    private boolean isExpectedConflict(Exception ex) {
        String className = ex.getClass().getSimpleName();
        return className.equals("BookingConflictException")
                || className.equals("ObjectOptimisticLockingFailureException")
                || className.equals("OptimisticLockException")
                || className.contains("OptimisticLock");
    }
}
