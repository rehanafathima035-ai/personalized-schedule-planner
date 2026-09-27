package com.lifeplanner.habit;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lifeplanner.habit.HabitCompletionEntities.HabitCompletion;
import com.lifeplanner.security.CurrentUser;

@RestController
@RequestMapping("/api/habits")
public class HabitCompletionController {

    private final HabitCompletionService service;
    private final CurrentUser currentUser;

    public HabitCompletionController(
            HabitCompletionService service,
            CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping("/{habitId}/complete")
    public HabitCompletion complete(
            @PathVariable Long habitId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {

        return service.complete(
                currentUser.requireId(),
                habitId,
                date);
    }

    @DeleteMapping("/{habitId}/complete")
    public void uncomplete(
            @PathVariable Long habitId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {

        service.uncomplete(
                currentUser.requireId(),
                habitId,
                date);
    }

    @GetMapping("/completions")
    public List<HabitCompletion> range(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to) {

        return service.forRange(
                currentUser.requireId(),
                from,
                to);
    }
}