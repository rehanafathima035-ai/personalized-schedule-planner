package com.lifeplanner.prayer;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lifeplanner.prayer.PrayerDtos.PrayerSettingRequest;
import com.lifeplanner.prayer.PrayerDtos.PrayerSettingResponse;
import com.lifeplanner.security.CurrentUser;

@RestController
@RequestMapping("/api/prayers")
public class PrayerController {

    private final PrayerService prayerService;
    private final CurrentUser currentUser;

    public PrayerController(
            PrayerService prayerService,
            CurrentUser currentUser) {
        this.prayerService = prayerService;
        this.currentUser = currentUser;
    }

    @GetMapping("/settings")
    public PrayerSettingResponse getSettings() {
        return prayerService.get(currentUser.requireId());
    }

    @PostMapping("/settings")
    public PrayerSettingResponse saveSettings(
            @RequestBody PrayerSettingRequest request) {
        return prayerService.save(
                currentUser.requireId(),
                request);
    }
}