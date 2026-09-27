package com.lifeplanner.goal;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.*;

public final class MilestoneEntities {

    private MilestoneEntities() {}

    @Entity
    @Table(name = "goal_milestone")
    public static class Milestone {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "goal_id", nullable = false)
        private Long goalId;

        @Column(nullable = false, length = 200)
        private String title;

        @Column(name = "due_date")
        private LocalDate dueDate;

        @Column(name = "sort_order", nullable = false)
        private Integer sortOrder = 0;

        @Column(name = "completed_at")
        private Instant completedAt;

        protected Milestone() {}

        public Milestone(Long goalId, String title) {
            this.goalId = goalId;
            this.title = title;
        }

        public Long getId() {
            return id;
        }

        public Long getGoalId() {
            return goalId;
        }

        public String getTitle() {
            return title;
        }

        public LocalDate getDueDate() {
            return dueDate;
        }

        public Integer getSortOrder() {
            return sortOrder;
        }

        public Instant getCompletedAt() {
            return completedAt;
        }

        public void setGoalId(Long goalId) {
            this.goalId = goalId;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public void setDueDate(LocalDate dueDate) {
            this.dueDate = dueDate;
        }

        public void setSortOrder(Integer sortOrder) {
            this.sortOrder = sortOrder;
        }

        public void setCompletedAt(Instant completedAt) {
            this.completedAt = completedAt;
        }
    }
}