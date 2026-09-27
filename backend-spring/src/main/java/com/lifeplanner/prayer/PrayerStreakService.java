package com.lifeplanner.prayer;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.lifeplanner.prayer.PrayerCompletionEntities.PrayerCompletion;
import com.lifeplanner.prayer.PrayerCompletionEntities.PrayerCompletionStatus;

@Service
public class PrayerStreakService {

    private final PrayerCompletionRepository repository;

    public PrayerStreakService(PrayerCompletionRepository repository) {
        this.repository = repository;
    }

    public StreakResult calculate(
            Long userId,
            LocalDate from,
            LocalDate to) {

        LocalDate today = LocalDate.now();

        List<PrayerCompletion> records =
                repository.findByUserIdAndPrayerDateBetweenOrderByPrayerDateAsc(
                        userId,
                        from,
                        to);

        Set<LocalDate> fullyCompletedDates = new HashSet<>();

        for (PrayerCompletion record : records) {
            if (record.getStatus() != PrayerCompletionStatus.COMPLETED) {
                continue;
            }

            LocalDate date = record.getPrayerDate();

            long completedCount = records.stream()
                    .filter(r ->
                            r.getPrayerDate().equals(date)
                            && r.getStatus() == PrayerCompletionStatus.COMPLETED)
                    .count();

            if (completedCount == 5) {
                fullyCompletedDates.add(date);
            }
        }

        int best = 0;
        int running = 0;

        LocalDate date = from;

        while (!date.isAfter(to) && !date.isAfter(today)) {

            if (fullyCompletedDates.contains(date)) {
                running++;
                best = Math.max(best, running);
            } else {
                running = 0;
            }

            date = date.plusDays(1);
        }

        int current = 0;

        date = today.isBefore(to) ? today : to;

        while (!date.isBefore(from)) {

            if (fullyCompletedDates.contains(date)) {
                current++;
            } else {
                break;
            }

            date = date.minusDays(1);
        }

        return new StreakResult(current, best);
    }

    public record StreakResult(
            int currentStreak,
            int bestStreak) {
    }
}