package com.lifeplanner.reminder;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderRepository
        extends JpaRepository<Reminder, Long> {

    List<Reminder> findByUserIdAndEnabledTrue(Long userId);

    List<Reminder> findByUserIdOrderByIdDesc(Long userId);

    Optional<Reminder> findByIdAndUserId(
            Long id,
            Long userId);
}