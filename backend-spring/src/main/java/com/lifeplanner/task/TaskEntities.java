package com.lifeplanner.task;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.*;

public final class TaskEntities {

    private TaskEntities() {}

    public enum Priority {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum Flexibility {
        FIXED, PREFERRED, FLEXIBLE
    }

    public enum TaskStatus {
        PENDING, COMPLETED, SKIPPED, RESCHEDULED
    }

    @Entity
    @Table(name = "task")
    public static class Task {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "user_id", nullable = false)
        private Long userId;

        @Column(name = "goal_id")
        private Long goalId;

        @Column(name = "milestone_id")
        private Long milestoneId;

        @Column(name = "habit_id")
        private Long habitId;

        @Column(nullable = false, length = 200)
        private String title;

        @Column(columnDefinition = "TEXT")
        private String description;

        @Column(length = 60)
        private String category;

        @Column(name = "scheduled_date")
        private LocalDate scheduledDate;

        @Column(name = "start_minute")
        private Short startMinute;

        @Column(name = "duration_minutes", nullable = false)
        private Short durationMinutes = 30;

        @Column(name = "due_date")
        private LocalDate dueDate;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 10)
        private Priority priority = Priority.MEDIUM;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 10)
        private Flexibility flexibility = Flexibility.FLEXIBLE;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 20)
        private TaskStatus status = TaskStatus.PENDING;

        @Column(name = "completed_at")
        private Instant completedAt;

        @Column(name = "is_demo", nullable = false)
        private boolean demo = false;

        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt = Instant.now();

        protected Task() {}

        public Task(Long userId, String title) {
            this.userId = userId;
            this.title = title;
        }

        public Long getId() { return id; }
        public Long getUserId() { return userId; }
        public Long getGoalId() { return goalId; }
        public Long getMilestoneId() { return milestoneId; }
        public Long getHabitId() { return habitId; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getCategory() { return category; }
        public LocalDate getScheduledDate() { return scheduledDate; }
        public Short getStartMinute() { return startMinute; }
        public Short getDurationMinutes() { return durationMinutes; }
        public LocalDate getDueDate() { return dueDate; }
        public Priority getPriority() { return priority; }
        public Flexibility getFlexibility() { return flexibility; }
        public TaskStatus getStatus() { return status; }
        public Instant getCompletedAt() { return completedAt; }
        public boolean isDemo() { return demo; }
        public Instant getCreatedAt() { return createdAt; }
        public void setUserId(Long userId) {this.userId = userId;}
        public void setGoalId(Long goalId) { this.goalId = goalId; }
        public void setMilestoneId(Long milestoneId) { this.milestoneId = milestoneId; }
        public void setHabitId(Long habitId) { this.habitId = habitId; }
        public void setTitle(String title) { this.title = title; }
        public void setDescription(String description) { this.description = description; }
        public void setCategory(String category) { this.category = category; }
        public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }
        public void setStartMinute(Short startMinute) { this.startMinute = startMinute; }
        public void setDurationMinutes(Short durationMinutes) { this.durationMinutes = durationMinutes; }
        public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
        public void setPriority(Priority priority) { this.priority = priority; }
        public void setFlexibility(Flexibility flexibility) { this.flexibility = flexibility; }
        public void setStatus(TaskStatus status) { this.status = status; }
        public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
        public void setDemo(boolean demo) { this.demo = demo; }
    }
}