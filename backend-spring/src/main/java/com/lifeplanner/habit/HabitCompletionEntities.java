package com.lifeplanner.habit;

import java.math.BigDecimal;
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

public final class HabitCompletionEntities {

    private HabitCompletionEntities() {}

    public enum CompletionStatus {
        COMPLETED,
        SKIPPED,
        MISSED
    }

    @Entity
    @Table(name = "habit_completion")
    public static class HabitCompletion {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "habit_id", nullable = false)
        private Long habitId;

        @Column(name = "user_id", nullable = false)
        private Long userId;

        @Column(name = "occurrence_date", nullable = false)
        private LocalDate occurrenceDate;

        @Enumerated(EnumType.STRING)
        @Column(name = "status", nullable = false, length = 9)
        private CompletionStatus status;

        @Column(name = "recorded_value")
        private BigDecimal recordedValue;

        @Column(name = "note", length = 500)
        private String note;

        @Column(name = "completed_at", nullable = false)
        private LocalDateTime completedAt;

        public HabitCompletion() {}

        public HabitCompletion(
                Long habitId,
                Long userId,
                LocalDate occurrenceDate) {
            this.habitId = habitId;
            this.userId = userId;
            this.occurrenceDate = occurrenceDate;
            this.status = CompletionStatus.COMPLETED;
            this.completedAt = LocalDateTime.now();
        }

        public Long getId() {
            return id;
        }

        public Long getHabitId() {
            return habitId;
        }

        public Long getUserId() {
            return userId;
        }

        public LocalDate getOccurrenceDate() {
            return occurrenceDate;
        }

        public CompletionStatus getStatus() {
            return status;
        }

        public BigDecimal getRecordedValue() {
            return recordedValue;
        }

        public String getNote() {
            return note;
        }

        public LocalDateTime getCompletedAt() {
            return completedAt;
        }

        public void setStatus(CompletionStatus status) {
            this.status = status;
        }

        public void setRecordedValue(BigDecimal recordedValue) {
            this.recordedValue = recordedValue;
        }

        public void setNote(String note) {
            this.note = note;
        }
    }
}