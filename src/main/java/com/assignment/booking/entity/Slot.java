package com.assignment.booking.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.assignment.booking.enums.SlotStatus;
import java.time.LocalDateTime;

@Entity
@Table(name = "slot")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Slot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SlotStatus status = SlotStatus.AVAILABLE;

    /**
     * Optimistic locking version column.
     * Hibernate automatically increments this on every UPDATE and includes
     * it in the WHERE clause (WHERE id = ? AND version = ?). If two
     * transactions read the same version and both try to update, only the
     * first commit succeeds; the second gets an
     * {@link jakarta.persistence.OptimisticLockException}, which we translate
     * into a 409 Conflict at the service layer. This is what prevents the
     * double-booking race condition without any in-memory locks.
     */
    @Version
    @Column(nullable = false)
    private Long version;
}
