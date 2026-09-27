package com.lifeplanner.habit;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.lifeplanner.habit.HabitCompletionEntities.CompletionStatus;
import com.lifeplanner.habit.HabitCompletionEntities.HabitCompletion;
import com.lifeplanner.schedule.ScheduleEntities.ScheduledItem;
import com.lifeplanner.schedule.ScheduleEntities.SourceType;
import com.lifeplanner.schedule.ScheduledItemRepository;

@Service
public class HabitStreakService {

    private final HabitCompletionRepository completions;
    private final ScheduledItemRepository scheduledItems;

    public HabitStreakService(
            HabitCompletionRepository completions,
            ScheduledItemRepository scheduledItems) {
        this.completions = completions;
        this.scheduledItems = scheduledItems;
    }

    public StreakResult calculate(
            Long userId,
            Long habitId,
            LocalDate from,
            LocalDate to) {

        LocalDate today = LocalDate.now();

        List<ScheduledItem> scheduled =
                scheduledItems
                        .findByUserIdAndSourceTypeAndSourceIdAndScheduledDateBetweenOrderByScheduledDateAscStartMinuteAsc(
                                userId,
                                SourceType.HABIT,
                                habitId,
                                from,
                                to);

        if (scheduled.isEmpty()) {
            return new StreakResult(0, 0);
        }

        List<HabitCompletion> records =
                completions
                        .findByUserIdAndHabitIdAndOccurrenceDateBetweenOrderByOccurrenceDateAsc(
                                userId,
                                habitId,
                                from,
                                to);

        Set<LocalDate> completedDates = records.stream()
                .filter(record ->
                        record.getStatus() == CompletionStatus.COMPLETED)
                .map(HabitCompletion::getOccurrenceDate)
                .collect(Collectors.toSet());

        List<LocalDate> scheduledDates = scheduled.stream()
                .map(ScheduledItem::getScheduledDate)
                .distinct()
                .sorted()
                .toList();

        int best = 0;
        int running = 0;

        for (LocalDate date : scheduledDates) {

            if (date.isAfter(today)) {
                continue;
            }

            if (completedDates.contains(date)) {
                running++;
                best = Math.max(best, running);
            } else {
                running = 0;
            }
        }

        int current = 0;

        for (int i = scheduledDates.size() - 1; i >= 0; i--) {

            LocalDate date = scheduledDates.get(i);

            if (date.isAfter(today)) {
                continue;
            }

            if (completedDates.contains(date)) {
                current++;
            } else {
                break;
            }
        }

        return new StreakResult(current, best);
    }

    public record StreakResult(
            int currentStreak,
            int bestStreak) {
    }
}