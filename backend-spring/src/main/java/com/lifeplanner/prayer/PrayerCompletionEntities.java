package com.lifeplanner.prayer;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

public final class PrayerCompletionEntities {

    private PrayerCompletionEntities() {}

    public enum PrayerCompletionStatus {
        COMPLETED,
        MISSED,
        EXEMPT
    }

    public enum PrayerName {
        FAJR,
        DHUHR,
        ASR,
        MAGHRIB,
        ISHA
    }

    @Entity
    @Table(name = "prayer_completion")
    public static class PrayerCompletion {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "user_id", nullable = false)
        private Long userId;

        @Column(name = "prayer_date", nullable = false)
        private LocalDate prayerDate;

        @Enumerated(EnumType.STRING)
        @Column(name = "prayer_name", nullable = false, length = 7)
        private PrayerName prayerName;

        @Enumerated(EnumType.STRING)
        @Column(name = "status", nullable = false, length = 9)
        private PrayerCompletionStatus status;

        @Column(name = "completed_at", nullable = false)
        private LocalDateTime completedAt;

        public PrayerCompletion() {}

        public PrayerCompletion(
                Long userId,
                LocalDate prayerDate,
                PrayerName prayerName) {
            this.userId = userId;
            this.prayerDate = prayerDate;
            this.prayerName = prayerName;
            this.status = PrayerCompletionStatus.COMPLETED;
            this.completedAt = LocalDateTime.now();
        }

        public Long getId() {
            return id;
        }

        public Long getUserId() {
            return userId;
        }

        public LocalDate getPrayerDate() {
            return prayerDate;
        }

        public PrayerName getPrayerName() {
            return prayerName;
        }

        public PrayerCompletionStatus getStatus() {
            return status;
        }

        public LocalDateTime getCompletedAt() {
            return completedAt;
        }

        public void setStatus(PrayerCompletionStatus status) {
            this.status = status;
        }
    }
}