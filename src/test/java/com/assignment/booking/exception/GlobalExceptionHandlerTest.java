package com.assignment.booking.exception;

import com.assignment.booking.dto.ErrorResponse;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Direct unit test for GlobalExceptionHandler — exercises every handler branch
 * independently of the HTTP layer so coverage gaps (ResourceNotFoundException,
 * IllegalArgumentException, AccessDeniedException, DataIntegrityViolationException,
 * generic Exception) are closed without needing full Spring context.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/test");
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    void handleValidation_returns400WithFieldMessages() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("obj", "field", "must not be blank");
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetails()).containsExactly("must not be blank");
    }

    // ── Not Found ─────────────────────────────────────────────────────────────

    @Test
    void handleNotFound_returns404() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Slot not found");

        ResponseEntity<ErrorResponse> response = handler.handleNotFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("Slot not found");
        assertThat(response.getBody().getPath()).isEqualTo("/test");
    }

    // ── Duplicate Username ────────────────────────────────────────────────────

    @Test
    void handleDuplicateUsername_returns409() {
        DuplicateUsernameException ex = new DuplicateUsernameException("alice");

        ResponseEntity<ErrorResponse> response = handler.handleDuplicateUsername(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).contains("alice");
    }

    // ── Booking Conflict — explicit ───────────────────────────────────────────

    @Test
    void handleBookingConflict_bookingConflictException_returns409WithOriginalMessage() {
        BookingConflictException ex = new BookingConflictException("Slot 1 is already booked");

        ResponseEntity<ErrorResponse> response = handler.handleBookingConflict(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo("Slot 1 is already booked");
    }

    // ── Booking Conflict — optimistic lock (JPA) ──────────────────────────────

    @Test
    void handleBookingConflict_optimisticLockException_returns409WithGenericMessage() {
        OptimisticLockException ex = new OptimisticLockException("version mismatch");

        ResponseEntity<ErrorResponse> response = handler.handleBookingConflict(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).contains("another user");
    }

    // ── Booking Conflict — optimistic lock (Spring ORM) ──────────────────────

    @Test
    void handleBookingConflict_objectOptimisticLockingFailure_returns409() {
        ObjectOptimisticLockingFailureException ex =
                new ObjectOptimisticLockingFailureException("Slot", new RuntimeException());

        ResponseEntity<ErrorResponse> response = handler.handleBookingConflict(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).contains("another user");
    }

    // ── Unauthorized Action ───────────────────────────────────────────────────

    @Test
    void handleUnauthorizedAction_returns403() {
        UnauthorizedActionException ex = new UnauthorizedActionException("Cannot cancel another user's booking");

        ResponseEntity<ErrorResponse> response = handler.handleUnauthorizedAction(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getMessage()).contains("Cannot cancel");
    }

    // ── AccessDeniedException (Spring Security) ───────────────────────────────

    @Test
    void handleAccessDenied_returns403WithStandardMessage() {
        AccessDeniedException ex = new AccessDeniedException("Access is denied");

        ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getMessage()).contains("permission");
    }

    // ── Bad Credentials ───────────────────────────────────────────────────────

    @Test
    void handleBadCredentials_returns401() {
        BadCredentialsException ex = new BadCredentialsException("Bad credentials");

        ResponseEntity<ErrorResponse> response = handler.handleBadCredentials(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getMessage()).contains("Invalid username");
    }

    // ── DataIntegrityViolation ────────────────────────────────────────────────

    @Test
    void handleDataIntegrity_returns409() {
        DataIntegrityViolationException ex =
                new DataIntegrityViolationException("constraint violation");

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).contains("data conflict");
    }

    // ── IllegalArgument ───────────────────────────────────────────────────────

    @Test
    void handleIllegalArgument_returns400() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid slot time range");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid slot time range");
    }

    // ── Generic fallback ──────────────────────────────────────────────────────

    @Test
    void handleGeneric_returns500() {
        Exception ex = new RuntimeException("Something went very wrong");

        ResponseEntity<ErrorResponse> response = handler.handleGeneric(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
    }
}
