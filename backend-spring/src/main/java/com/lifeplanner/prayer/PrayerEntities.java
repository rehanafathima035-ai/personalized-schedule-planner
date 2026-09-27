package com.lifeplanner.prayer;

import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

public final class PrayerEntities {

    private PrayerEntities() {}

    @Entity
    @Table(name = "prayer_setting")
    public static class PrayerSetting {

        @Id
        @Column(name = "user_id", nullable = false)
        private Long userId;

        @Column(name = "fajr_time")
        private LocalTime fajrTime;

        @Column(name = "dhuhr_time")
        private LocalTime dhuhrTime;

        @Column(name = "asr_time")
        private LocalTime asrTime;

        @Column(name = "maghrib_time")
        private LocalTime maghribTime;

        @Column(name = "isha_time")
        private LocalTime ishaTime;

        public PrayerSetting() {}

        public PrayerSetting(
                Long userId,
                LocalTime fajrTime,
                LocalTime dhuhrTime,
                LocalTime asrTime,
                LocalTime maghribTime,
                LocalTime ishaTime) {
            this.userId = userId;
            this.fajrTime = fajrTime;
            this.dhuhrTime = dhuhrTime;
            this.asrTime = asrTime;
            this.maghribTime = maghribTime;
            this.ishaTime = ishaTime;
        }

        public Long getUserId() {
            return userId;
        }

        public LocalTime getFajrTime() {
            return fajrTime;
        }

        public void setFajrTime(LocalTime fajrTime) {
            this.fajrTime = fajrTime;
        }

        public LocalTime getDhuhrTime() {
            return dhuhrTime;
        }

        public void setDhuhrTime(LocalTime dhuhrTime) {
            this.dhuhrTime = dhuhrTime;
        }

        public LocalTime getAsrTime() {
            return asrTime;
        }

        public void setAsrTime(LocalTime asrTime) {
            this.asrTime = asrTime;
        }

        public LocalTime getMaghribTime() {
            return maghribTime;
        }

        public void setMaghribTime(LocalTime maghribTime) {
            this.maghribTime = maghribTime;
        }

        public LocalTime getIshaTime() {
            return ishaTime;
        }

        public void setIshaTime(LocalTime ishaTime) {
            this.ishaTime = ishaTime;
        }
    }
}