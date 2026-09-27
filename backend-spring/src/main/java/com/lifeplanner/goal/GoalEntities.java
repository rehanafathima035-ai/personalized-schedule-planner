package com.lifeplanner.goal;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.*;

public final class GoalEntities {

    private GoalEntities() {}

    public enum GoalStatus {
        ACTIVE, PAUSED, ACHIEVED, ABANDONED
    }

    @Entity
    @Table(name = "goal")
    public static class Goal {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "user_id", nullable = false)
        private Long userId;

        @Column(nullable = false, length = 200)
        private String title;

        @Column(columnDefinition = "TEXT")
        private String description;

        @Column(length = 60)
        private String category;

        @Column(name = "target_year")
        private Short targetYear;

        @Column(name = "target_date")
        private LocalDate targetDate;

        @Column(name = "target_count", nullable = false)
        private Integer targetCount = 1;

        @Column(name = "achieved_count", nullable = false)
        private Integer achievedCount = 0;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 10)
        private GoalStatus status = GoalStatus.ACTIVE;

        @Column(name = "is_demo", nullable = false)
        private boolean demo = false;

        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt = Instant.now();

        protected Goal() {}

        public Goal(Long userId, String title) {
            this.userId = userId;
            this.title = title;
        }

        public Long getId() {
            return id;
        }

        public Long getUserId() {
            return userId;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public String getCategory() {
            return category;
        }

        public Short getTargetYear() {
            return targetYear;
        }

        public LocalDate getTargetDate() {
            return targetDate;
        }

        public Integer getTargetCount() {
            return targetCount;
        }

        public Integer getAchievedCount() {
            return achievedCount;
        }

        public GoalStatus getStatus() {
            return status;
        }

        public boolean isDemo() {
            return demo;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public void setTargetYear(Short targetYear) {
            this.targetYear = targetYear;
        }

        public void setTargetDate(LocalDate targetDate) {
            this.targetDate = targetDate;
        }

        public void setTargetCount(Integer targetCount) {
            this.targetCount = targetCount;
        }

        public void setAchievedCount(Integer achievedCount) {
            this.achievedCount = achievedCount;
        }

        public void setStatus(GoalStatus status) {
            this.status = status;
        }

        public void setDemo(boolean demo) {
            this.demo = demo;
        }
    }
}