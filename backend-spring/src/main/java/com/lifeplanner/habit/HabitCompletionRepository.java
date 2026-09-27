package com.lifeplanner.habit;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.lifeplanner.habit.HabitCompletionEntities.CompletionStatus;
import com.lifeplanner.habit.HabitCompletionEntities.HabitCompletion;

@Repository
public interface HabitCompletionRepository
        extends JpaRepository<HabitCompletion, Long> {

    Optional<HabitCompletion> findByUserIdAndHabitIdAndOccurrenceDate(
            Long userId,
            Long habitId,
            LocalDate occurrenceDate);

    List<HabitCompletion> findByUserIdAndHabitIdAndOccurrenceDateBetweenOrderByOccurrenceDateAsc(
            Long userId,
            Long habitId,
            LocalDate from,
            LocalDate to);

    List<HabitCompletion> findByUserIdAndOccurrenceDateBetweenOrderByOccurrenceDateAsc(
            Long userId,
            LocalDate from,
            LocalDate to);

    long countByUserIdAndOccurrenceDateBetweenAndStatus(
            Long userId,
            LocalDate from,
            LocalDate to,
            CompletionStatus status);
}