package com.assignment.booking.repository;

import com.assignment.booking.entity.Slot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlotRepository extends JpaRepository<Slot, Long> {
    // findById() from JpaRepository is used to read the current version
    // before attempting an update; Hibernate's @Version handling on save()
    // is what enforces the optimistic-lock check.
}
