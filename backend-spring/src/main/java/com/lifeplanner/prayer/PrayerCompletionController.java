package com.lifeplanner.prayer;

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

import com.lifeplanner.prayer.PrayerCompletionEntities.PrayerCompletion;
import com.lifeplanner.prayer.PrayerCompletionEntities.PrayerName;
import com.lifeplanner.security.CurrentUser;

@RestController
@RequestMapping("/api/prayers/completions")
public class PrayerCompletionController {

    private final PrayerCompletionService service;
    private final CurrentUser currentUser;

    public PrayerCompletionController(
            PrayerCompletionService service,
            CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping("/{prayerName}")
    public PrayerCompletion complete(
            @PathVariable PrayerName prayerName,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {

        return service.complete(
                currentUser.requireId(),
                date,
                prayerName);
    }

    @DeleteMapping("/{prayerName}")
    public void uncomplete(
            @PathVariable PrayerName prayerName,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {

        service.uncomplete(
                currentUser.requireId(),
                date,
                prayerName);
    }

    @GetMapping
    public List<PrayerCompletion> range(
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