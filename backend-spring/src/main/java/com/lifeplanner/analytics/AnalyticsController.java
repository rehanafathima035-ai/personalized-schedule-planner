package com.lifeplanner.analytics;

import java.time.LocalDate;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lifeplanner.security.CurrentUser;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/analytics")
@SecurityRequirement(name = "bearerAuth")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final CurrentUser currentUser;

    public AnalyticsController(
            AnalyticsService analyticsService,
            CurrentUser currentUser) {

        this.analyticsService = analyticsService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public AnalyticsService.AnalyticsResult analytics(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {

        Long userId = currentUser.requireId();

        return analyticsService.calculate(
                userId,
                from,
                to);
    }
}