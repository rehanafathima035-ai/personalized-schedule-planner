package com.lifeplanner.analytics;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.lifeplanner.habit.HabitCompletionEntities.CompletionStatus;
import com.lifeplanner.habit.HabitCompletionEntities.HabitCompletion;
import com.lifeplanner.habit.HabitCompletionRepository;
import com.lifeplanner.prayer.PrayerCompletionEntities.PrayerCompletion;
import com.lifeplanner.prayer.PrayerCompletionEntities.PrayerCompletionStatus;
import com.lifeplanner.prayer.PrayerCompletionRepository;

@Service
public class AnalyticsService {

    private final HabitCompletionRepository habitCompletions;
    private final PrayerCompletionRepository prayerCompletions;

    public AnalyticsService(
            HabitCompletionRepository habitCompletions,
            PrayerCompletionRepository prayerCompletions) {

        this.habitCompletions = habitCompletions;
        this.prayerCompletions = prayerCompletions;
    }

    public AnalyticsResult calculate(
            Long userId,
            LocalDate from,
            LocalDate to) {

        List<HabitCompletion> habits =
                habitCompletions
                        .findByUserIdAndOccurrenceDateBetweenOrderByOccurrenceDateAsc(
                                userId,
                                from,
                                to);

        List<PrayerCompletion> prayers =
                prayerCompletions
                        .findByUserIdAndPrayerDateBetweenOrderByPrayerDateAsc(
                                userId,
                                from,
                                to);

        long habitCompleted = habits.stream()
                .filter(h -> h.getStatus() == CompletionStatus.COMPLETED)
                .count();

        long habitTotal = habits.size();

        long prayerCompleted = prayers.stream()
                .filter(p -> p.getStatus() == PrayerCompletionStatus.COMPLETED)
                .count();

        long prayerTotal = prayers.size();

        long totalCompleted = habitCompleted + prayerCompleted;
        long totalItems = habitTotal + prayerTotal;

        double completionPercentage =
                totalItems == 0
                        ? 0.0
                        : (totalCompleted * 100.0) / totalItems;

        return new AnalyticsResult(
                from,
                to,
                habitCompleted,
                habitTotal,
                prayerCompleted,
                prayerTotal,
                totalCompleted,
                totalItems,
                Math.round(completionPercentage * 100.0) / 100.0);
    }

    public record AnalyticsResult(
            LocalDate from,
            LocalDate to,
            long habitCompleted,
            long habitTotal,
            long prayerCompleted,
            long prayerTotal,
            long totalCompleted,
            long totalItems,
            double completionPercentage) {
    }
}