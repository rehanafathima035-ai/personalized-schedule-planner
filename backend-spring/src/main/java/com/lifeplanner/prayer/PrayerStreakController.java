package com.lifeplanner.prayer;

import java.time.LocalDate;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.lifeplanner.security.CurrentUser;
@RestController
@RequestMapping("/api/prayers")
@SecurityRequirement(name = "bearerAuth")
public class PrayerStreakController {

    private final PrayerStreakService streakService;
    private final CurrentUser currentUser;

    public PrayerStreakController(
            PrayerStreakService streakService,
            CurrentUser currentUser) {
        this.streakService = streakService;
        this.currentUser = currentUser;
    }

    @GetMapping("/streak")
    public PrayerStreakService.StreakResult streak() {

        Long userId = currentUser.requireId();

        return streakService.calculate(
                userId,
                LocalDate.of(2000, 1, 1),
                LocalDate.now());
    }
}