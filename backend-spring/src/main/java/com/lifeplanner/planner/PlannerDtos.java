package com.lifeplanner.planner;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Wire format for the FastAPI planner service.
 *
 * These mirror the Pydantic models in planner-service/app/schemas/planner.py.
 * Keeping them as records makes the contract explicit and reviewable.
 */
public final class PlannerDtos {

    private PlannerDtos() {}

    public record ActivityPayload(
            String id,
            String name,
            int duration_minutes,
            int occurrences,
            String kind,
            String priority,
            String flexibility,
            List<String> preferred_days,
            List<String> excluded_days,
            String preferred_time_of_day,
            Integer preferred_start_minute,
            Integer min_spacing_days,
            Integer max_spacing_days,
            Integer max_per_day,
            LocalDate deadline,
            List<String> depends_on) {}

    public record CommitmentPayload(
            String id, String name, LocalDate day,
            int start_minute, int end_minute) {}

    public record RelationshipPayload(
            String activity_a, String activity_b, String type, int days, String note) {}

    public record ScheduledItemPayload(
            String activity_id, String activity_name, LocalDate day,
            int start_minute, int end_minute) {}

    public record PlanPayload(
            LocalDate horizon_start,
            int horizon_days,
            List<ActivityPayload> activities,
            List<CommitmentPayload> fixed_commitments,
            List<RelationshipPayload> relationships,
            List<ScheduledItemPayload> locked_items,
            String strategy) {}

    public record WhatIfPayload(PlanPayload plan, ActivityPayload hypothetical) {}

    public record RebalancePayload(PlanPayload plan, List<ActivityPayload> missed) {}

    public record InterpretPayload(String text) {}

    // -- responses ------------------------------------------------------

    public record ProposalItem(
            String activity_id, String activity_name, LocalDate day, String weekday,
            int start_minute, int end_minute, List<String> reason_codes) {}

    public record ProposalConflict(
            String type, String severity, String message,
            List<String> activity_ids, LocalDate day, Map<String, String> details) {}

    public record UnplacedEntry(String activity_id, int missing_occurrences) {}

    public record ProposalResponse(
            String strategy, double score,
            List<ProposalItem> items,
            List<ProposalConflict> conflicts,
            List<UnplacedEntry> unplaced,
            List<String> explanations,
            boolean has_hard_conflicts,
            String status) {}

    public record PlanResponse(
            ProposalResponse proposal,
            List<ProposalResponse> alternatives,
            List<Map<String, Object>> balance) {}

    public record InterpretResponse(
            Map<String, Object> interpretation, boolean requires_clarification, String status) {}
    public record ConflictResponse(
            List<ProposalConflict> conflicts,
            List<Map<String, Object>> balance,
            boolean has_hard_conflicts) {}   
}
