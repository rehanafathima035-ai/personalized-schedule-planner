package com.lifeplanner.habit;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class HabitDtos {

    private HabitDtos() {}

    /** A habit the user is describing. Valid as a draft; not yet saved. */
    public record HabitDraft(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 2000) String description,
            @Size(max = 60) String category,
            Enums.TrackingType trackingType,
            @Min(1) @Max(1440) Integer durationMinutes,
            @Min(1) @Max(100) Integer occurrences,
            Enums.FrequencyType frequencyType,
            List<String> preferredDays,
            List<String> excludedDays,
            Enums.TimeOfDay preferredTimeOfDay,
            @Min(0) @Max(1439) Integer preferredStartMinute,
            @Min(0) @Max(365) Integer minSpacingDays,
            @Min(1) @Max(365) Integer maxSpacingDays,
            Enums.Priority priority,
            Enums.Flexibility flexibility,
            LocalDate startDate,
            LocalDate endDate,
            Long goalId) {

        public Integer durationOrDefault() {
            return durationMinutes == null ? 30 : durationMinutes;
        }

        public Integer occurrencesOrDefault() {
            return occurrences == null ? 1 : occurrences;
        }

        public Enums.Priority priorityOrDefault() {
            return priority == null ? Enums.Priority.MEDIUM : priority;
        }

        public Enums.Flexibility flexibilityOrDefault() {
            return flexibility == null ? Enums.Flexibility.FLEXIBLE : flexibility;
        }

        public Enums.TimeOfDay timeOfDayOrDefault() {
            return preferredTimeOfDay == null ? Enums.TimeOfDay.ANY : preferredTimeOfDay;
        }
    }

    /** Request for a preview: either free text, a structured draft, or both. */
    public record PreviewRequest(
            String text,
            HabitDraft draft,
            LocalDate horizonStart,
            @Min(1) @Max(90) Integer horizonDays) {}

    public record HabitSummary(
            Long id, String name, String category, Enums.TrackingType trackingType,
            Integer durationMinutes, Integer occurrences, Enums.Priority priority,
            Enums.Flexibility flexibility, Enums.HabitStatus status,
            List<String> preferredDays, Enums.TimeOfDay preferredTimeOfDay,
            Integer preferredStartMinute, LocalDate startDate) {

        public static HabitSummary of(Habit habit) {
            HabitSchedule schedule = habit.getSchedule();
            return new HabitSummary(
                    habit.getId(), habit.getName(), habit.getCategory(),
                    habit.getTrackingType(), habit.getDurationMinutes(),
                    schedule == null ? null : schedule.getTimesPerPeriod(),
                    habit.getPriority(), habit.getFlexibility(), habit.getStatus(),
                    schedule == null ? List.of() : schedule.dayNames(),
                    schedule == null ? Enums.TimeOfDay.ANY : schedule.getPreferredTimeOfDay(),
                    schedule == null ? null : schedule.getPreferredStartMinute(),
                    habit.getStartDate());
        }
    }
}
