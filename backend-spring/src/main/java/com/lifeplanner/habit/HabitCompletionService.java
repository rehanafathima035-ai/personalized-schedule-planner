package com.lifeplanner.habit;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lifeplanner.common.ApiExceptions;
import com.lifeplanner.habit.HabitCompletionEntities.CompletionStatus;
import com.lifeplanner.habit.HabitCompletionEntities.HabitCompletion;

@Service
public class HabitCompletionService {

    private final HabitCompletionRepository completions;
    private final HabitRepository habits;

    public HabitCompletionService(
            HabitCompletionRepository completions,
            HabitRepository habits) {
        this.completions = completions;
        this.habits = habits;
    }

    @Transactional
    public HabitCompletion complete(
            Long userId,
            Long habitId,
            LocalDate date) {

        habits.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException(
                        "That habit does not exist."));

        HabitCompletion completion =
                completions
                        .findByUserIdAndHabitIdAndOccurrenceDate(
                                userId,
                                habitId,
                                date)
                        .orElse(null);

        if (completion == null) {
            completion = new HabitCompletion(
                    habitId,
                    userId,
                    date);
        } else {
            completion.setStatus(CompletionStatus.COMPLETED);
        }

        return completions.save(completion);
    }

    @Transactional
    public void uncomplete(
            Long userId,
            Long habitId,
            LocalDate date) {

        completions
                .findByUserIdAndHabitIdAndOccurrenceDate(
                        userId,
                        habitId,
                        date)
                .ifPresent(completions::delete);
    }

    public List<HabitCompletion> forRange(
            Long userId,
            LocalDate from,
            LocalDate to) {

        return completions
                .findByUserIdAndOccurrenceDateBetweenOrderByOccurrenceDateAsc(
                        userId,
                        from,
                        to);
    }
}