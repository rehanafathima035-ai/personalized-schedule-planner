package com.lifeplanner.schedule;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.*;

public final class ScheduleEntities {

    private ScheduleEntities() {}

    public enum ProposalOrigin {
        NEW_HABIT, NEW_GOAL, OPTIMIZE, REBALANCE, WHAT_IF, NATURAL_LANGUAGE
    }

    public enum ProposalStatus {
        PENDING, APPROVED, REJECTED, SUPERSEDED
    }

    public enum SourceType {
        HABIT, TASK, EVENT, PRAYER
    }

    @Entity
    @Table(name = "schedule_proposal")
    public static class ScheduleProposal {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "user_id", nullable = false)
        private Long userId;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 20)
        private ProposalOrigin origin;

        @Column(nullable = false, length = 32)
        private String strategy = "SPREAD";

        @Column(name = "horizon_start", nullable = false)
        private LocalDate horizonStart;

        @Column(name = "horizon_days", nullable = false)
        private Integer horizonDays;

        private Double score;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 12)
        private ProposalStatus status = ProposalStatus.PENDING;

        @Lob
        @Column(name = "explanation_json", columnDefinition = "TEXT")
        private String explanationJson;

        @Lob
        @Column(name = "draft_json", columnDefinition = "TEXT")
        private String draftJson;

        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt = Instant.now();

        @Column(name = "decided_at")
        private Instant decidedAt;

        @OneToMany(mappedBy = "proposal", cascade = CascadeType.ALL, orphanRemoval = true)
        private List<ScheduleProposalItem> items = new ArrayList<>();

        @OneToMany(mappedBy = "proposal", cascade = CascadeType.ALL, orphanRemoval = true)
        private List<ScheduleConflict> conflicts = new ArrayList<>();

        protected ScheduleProposal() {}

        public ScheduleProposal(
                Long userId,
                ProposalOrigin origin,
                LocalDate horizonStart,
                Integer horizonDays) {
            this.userId = userId;
            this.origin = origin;
            this.horizonStart = horizonStart;
            this.horizonDays = horizonDays;
        }

        public Long getId() {
            return id;
        }

        public Long getUserId() {
            return userId;
        }

        public ProposalOrigin getOrigin() {
            return origin;
        }

        public String getStrategy() {
            return strategy;
        }

        public LocalDate getHorizonStart() {
            return horizonStart;
        }

        public Integer getHorizonDays() {
            return horizonDays;
        }

        public Double getScore() {
            return score;
        }

        public ProposalStatus getStatus() {
            return status;
        }

        public String getExplanationJson() {
            return explanationJson;
        }

        public String getDraftJson() {
            return draftJson;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public Instant getDecidedAt() {
            return decidedAt;
        }

        public List<ScheduleProposalItem> getItems() {
            return items;
        }

        public List<ScheduleConflict> getConflicts() {
            return conflicts;
        }

        public void setStrategy(String s) {
            this.strategy = s;
        }

        public void setScore(Double s) {
            this.score = s;
        }

        public void setExplanationJson(String json) {
            this.explanationJson = json;
        }

        public void setDraftJson(String json) {
            this.draftJson = json;
        }

        public void addItem(ScheduleProposalItem item) {
            items.add(item);
            item.setProposal(this);
        }

        public void addConflict(ScheduleConflict conflict) {
            conflicts.add(conflict);
            conflict.setProposal(this);
        }

        public void markDecided(ProposalStatus decision) {
            this.status = decision;
            this.decidedAt = Instant.now();
        }
    }

    @Entity
    @Table(name = "schedule_proposal_item")
    public static class ScheduleProposalItem {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "proposal_id", nullable = false)
        private ScheduleProposal proposal;

        @Enumerated(EnumType.STRING)
        @Column(name = "source_type", nullable = false, length = 10)
        private SourceType sourceType = SourceType.HABIT;

        @Column(name = "source_id")
        private Long sourceId;

        @Column(name = "display_name", nullable = false, length = 160)
        private String displayName;

        @Column(name = "scheduled_date", nullable = false)
        private LocalDate scheduledDate;

        @Column(name = "start_minute", nullable = false)
        private Integer startMinute;

        @Column(name = "end_minute", nullable = false)
        private Integer endMinute;

        @Lob
        @Column(name = "reason_codes", columnDefinition = "TEXT")
        private String reasonCodes;

        protected ScheduleProposalItem() {}

        public ScheduleProposalItem(
                String displayName,
                LocalDate scheduledDate,
                Integer startMinute,
                Integer endMinute) {
            this.displayName = displayName;
            this.scheduledDate = scheduledDate;
            this.startMinute = startMinute;
            this.endMinute = endMinute;
        }

        public Long getId() {
            return id;
        }

        public ScheduleProposal getProposal() {
            return proposal;
        }

        public SourceType getSourceType() {
            return sourceType;
        }

        public Long getSourceId() {
            return sourceId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public LocalDate getScheduledDate() {
            return scheduledDate;
        }

        public Integer getStartMinute() {
            return startMinute;
        }

        public Integer getEndMinute() {
            return endMinute;
        }

        public String getReasonCodes() {
            return reasonCodes;
        }

        public void setProposal(ScheduleProposal p) {
            this.proposal = p;
        }

        public void setSourceType(SourceType t) {
            this.sourceType = t;
        }

        public void setSourceId(Long id) {
            this.sourceId = id;
        }

        public void setReasonCodes(String codes) {
            this.reasonCodes = codes;
        }
    }

    @Entity
    @Table(name = "schedule_conflict")
    public static class ScheduleConflict {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "proposal_id", nullable = false)
        private ScheduleProposal proposal;

        @Column(name = "conflict_type", nullable = false, length = 40)
        private String conflictType;

        @Column(nullable = false, length = 10)
        private String severity;

        @Column(nullable = false, length = 600)
        private String message;

        @Column(name = "conflict_date")
        private LocalDate conflictDate;

        protected ScheduleConflict() {}

        public ScheduleConflict(
                String conflictType,
                String severity,
                String message,
                LocalDate conflictDate) {
            this.conflictType = conflictType;
            this.severity = severity;
            this.message = message;
            this.conflictDate = conflictDate;
        }

        public Long getId() {
            return id;
        }

        public String getConflictType() {
            return conflictType;
        }

        public String getSeverity() {
            return severity;
        }

        public String getMessage() {
            return message;
        }

        public LocalDate getConflictDate() {
            return conflictDate;
        }

        public void setProposal(ScheduleProposal p) {
            this.proposal = p;
        }
    }

    @Entity
    @Table(name = "scheduled_item")
    public static class ScheduledItem {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "user_id", nullable = false)
        private Long userId;

        @Enumerated(EnumType.STRING)
        @Column(name = "source_type", nullable = false, length = 10)
        private SourceType sourceType;

        @Column(name = "source_id", nullable = false)
        private Long sourceId;

        @Column(name = "display_name", nullable = false, length = 160)
        private String displayName;

        @Column(name = "scheduled_date", nullable = false)
        private LocalDate scheduledDate;

        @Column(name = "start_minute", nullable = false)
        private Integer startMinute;

        @Column(name = "end_minute", nullable = false)
        private Integer endMinute;

        @Column(name = "approved_from_proposal")
        private Long approvedFromProposal;

        protected ScheduledItem() {}

        public ScheduledItem(
                Long userId,
                SourceType sourceType,
                Long sourceId,
                String displayName,
                LocalDate scheduledDate,
                Integer startMinute,
                Integer endMinute,
                Long approvedFromProposal) {
            this.userId = userId;
            this.sourceType = sourceType;
            this.sourceId = sourceId;
            this.displayName = displayName;
            this.scheduledDate = scheduledDate;
            this.startMinute = startMinute;
            this.endMinute = endMinute;
            this.approvedFromProposal = approvedFromProposal;
        }

        public ScheduledItem(
                Long id,
                Long userId,
                SourceType sourceType,
                Long sourceId,
                String displayName,
                LocalDate scheduledDate,
                Integer startMinute,
                Integer endMinute,
                Long approvedFromProposal) {
            this.id = id;
            this.userId = userId;
            this.sourceType = sourceType;
            this.sourceId = sourceId;
            this.displayName = displayName;
            this.scheduledDate = scheduledDate;
            this.startMinute = startMinute;
            this.endMinute = endMinute;
            this.approvedFromProposal = approvedFromProposal;
        }

        public Long getId() {
            return id;
        }

        public Long getUserId() {
            return userId;
        }

        public SourceType getSourceType() {
            return sourceType;
        }

        public Long getSourceId() {
            return sourceId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public LocalDate getScheduledDate() {
            return scheduledDate;
        }

        public Integer getStartMinute() {
            return startMinute;
        }

        public Integer getEndMinute() {
            return endMinute;
        }

        public Long getApprovedFromProposal() {
            return approvedFromProposal;
        }
    }
}
