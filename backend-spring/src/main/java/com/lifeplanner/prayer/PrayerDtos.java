package com.lifeplanner.prayer;

import java.time.LocalTime;

public final class PrayerDtos {

    private PrayerDtos() {}

    public record PrayerSettingRequest(
            LocalTime fajrTime,
            LocalTime dhuhrTime,
            LocalTime asrTime,
            LocalTime maghribTime,
            LocalTime ishaTime) {}

    public record PrayerSettingResponse(
            LocalTime fajrTime,
            LocalTime dhuhrTime,
            LocalTime asrTime,
            LocalTime maghribTime,
            LocalTime ishaTime) {}
}