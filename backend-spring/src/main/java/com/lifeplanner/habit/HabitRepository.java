package com.lifeplanner.habit;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HabitRepository extends JpaRepository<Habit, Long> {

    List<Habit> findByUserIdAndStatus(Long userId, Enums.HabitStatus status);

    List<Habit> findByUserId(Long userId);
    long countByUserIdAndGoalId(Long userId, Long goalId);

    /** Always fetch by id AND owner, so a guessed id returns nothing. */
    Optional<Habit> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndNameIgnoreCaseAndStatus(
            Long userId, String name, Enums.HabitStatus status);
}
