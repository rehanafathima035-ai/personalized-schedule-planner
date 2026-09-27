package com.lifeplanner.analytics;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lifeplanner.habit.HabitCompletionEntities.HabitCompletion;
import com.lifeplanner.habit.HabitCompletionRepository;
import com.lifeplanner.prayer.PrayerCompletionEntities.PrayerCompletion;
import com.lifeplanner.prayer.PrayerCompletionRepository;
import com.lifeplanner.security.CurrentUser;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/completion-history")
@SecurityRequirement(name = "bearerAuth")
public class CompletionHistoryController {

    private final HabitCompletionRepository habitCompletions;
    private final PrayerCompletionRepository prayerCompletions;
    private final CurrentUser currentUser;

    public CompletionHistoryController(
            HabitCompletionRepository habitCompletions,
            PrayerCompletionRepository prayerCompletions,
            CurrentUser currentUser) {

        this.habitCompletions = habitCompletions;
        this.prayerCompletions = prayerCompletions;
        this.currentUser = currentUser;
    }

    @GetMapping
    public CompletionHistory history(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to) {

        Long userId = currentUser.requireId();

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

        return new CompletionHistory(
                from,
                to,
                habits,
                prayers);
    }

    public record CompletionHistory(
            LocalDate from,
            LocalDate to,
            List<HabitCompletion> habits,
            List<PrayerCompletion> prayers) {
    }
}