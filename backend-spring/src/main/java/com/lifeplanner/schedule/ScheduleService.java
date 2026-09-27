package com.lifeplanner.schedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.lifeplanner.planner.PlannerClient;
import com.lifeplanner.planner.PlannerDtos;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeplanner.common.ApiExceptions;
import com.lifeplanner.habit.Enums;
import com.lifeplanner.habit.Habit;
import com.lifeplanner.habit.HabitDtos;
import com.lifeplanner.habit.HabitRepository;
import com.lifeplanner.habit.HabitSchedule;
import com.lifeplanner.prayer.PrayerSettingRepository;
import com.lifeplanner.schedule.ScheduleEntities.ProposalStatus;
import com.lifeplanner.schedule.ScheduleEntities.ScheduleProposal;
import com.lifeplanner.schedule.ScheduleEntities.ScheduleProposalItem;
import com.lifeplanner.schedule.ScheduleEntities.ScheduledItem;
import com.lifeplanner.schedule.ScheduleEntities.SourceType;
import com.lifeplanner.task.TaskEntities.Task;
import com.lifeplanner.task.TaskRepository;

@Service
public class ScheduleService {

    private final ProposalRepository proposals;
    private final ScheduledItemRepository scheduledItems;
    private final HabitRepository habits;
    private final PrayerSettingRepository prayerSettings;
    private final TaskRepository tasks;
    private final ObjectMapper objectMapper;
    private final PlannerClient planner;

    public ScheduleService(
            ProposalRepository proposals,
            ScheduledItemRepository scheduledItems,
            HabitRepository habits,
            PrayerSettingRepository prayerSettings,
            TaskRepository tasks,
            PlannerClient planner,
            ObjectMapper objectMapper) {

        this.proposals = proposals;
        this.scheduledItems = scheduledItems;
        this.habits = habits;
        this.tasks = tasks;
        this.prayerSettings = prayerSettings;
        this.planner = planner;
        this.objectMapper = objectMapper;
    }

    public List<ScheduleProposal> pending(Long userId) {

        return proposals.findByUserIdAndStatusOrderByCreatedAtDesc(
                userId,
                ProposalStatus.PENDING);
    }

    private ScheduleProposal requirePending(
            Long userId,
            Long proposalId) {

        ScheduleProposal proposal =
                proposals.findByIdAndUserId(
                        proposalId,
                        userId)
                        .orElseThrow(() ->
                                new ApiExceptions.NotFoundException(
                                        "That proposal does not exist."));

        if (proposal.getStatus() != ProposalStatus.PENDING) {
            throw new ApiExceptions.ConflictException(
                    "That proposal was already "
                            + proposal.getStatus().name().toLowerCase()
                            + ".");
        }

        return proposal;
    }

    @Transactional
    public Map<String, Object> approve(
            Long userId,
            Long proposalId) {

        ScheduleProposal proposal =
                requirePending(userId, proposalId);

        Long sourceId;
        String sourceName;

        if (proposal.getDraftJson() != null) {

            Habit habit =
                    createHabitFromDraft(
                            userId,
                            proposal.getDraftJson());

            sourceId = habit.getId();
            sourceName = habit.getName();

        } else {

            throw new ApiExceptions.ConflictException(
                    "This proposal has no draft attached and cannot be approved.");
        }

        for (ScheduleProposalItem item :
                proposal.getItems()) {

            scheduledItems.save(
                    new ScheduledItem(
                            userId,
                            SourceType.HABIT,
                            sourceId,
                            item.getDisplayName(),
                            item.getScheduledDate(),
                            item.getStartMinute(),
                            item.getEndMinute(),
                            proposal.getId()));
        }

        proposal.markDecided(
                ProposalStatus.APPROVED);

        proposals.save(proposal);

        return Map.of(
                "status",
                "APPROVED",
                "habitId",
                sourceId,
                "habitName",
                sourceName,
                "scheduledCount",
                proposal.getItems().size());
    }

    @Transactional
    public Map<String, Object> reject(
            Long userId,
            Long proposalId) {

        ScheduleProposal proposal =
                requirePending(
                        userId,
                        proposalId);

        proposal.markDecided(
                ProposalStatus.REJECTED);

        proposals.save(proposal);

        return Map.of(
                "status",
                "REJECTED",
                "message",
                "Nothing was added to your schedule.");
    }

    @Transactional
    public Map<String, Object> delete(
            Long userId,
            Long scheduledItemId) {

        ScheduledItem item =
                scheduledItems.findByIdAndUserId(
                        scheduledItemId,
                        userId)
                        .orElseThrow(() ->
                                new ApiExceptions.NotFoundException(
                                        "That scheduled item does not exist."));

        if (item.getSourceType() == SourceType.PRAYER) {
            throw new ApiExceptions.ConflictException(
                    "Prayer times cannot be deleted from the schedule.");
        }

        scheduledItems.delete(item);

        return Map.of(
                "status",
                "DELETED",
                "id",
                scheduledItemId,
                "message",
                "Scheduled item removed.");
    }

    private Habit createHabitFromDraft(
            Long userId,
            String draftJson) {

        HabitDtos.HabitDraft draft;

        try {
            draft = objectMapper.readValue(
                    draftJson,
                    HabitDtos.HabitDraft.class);

        } catch (Exception ex) {

            throw new IllegalStateException(
                    "Stored draft could not be read",
                    ex);
        }

        Habit habit =
                new Habit(
                        userId,
                        draft.name());

        habit.setDescription(
                draft.description());

        habit.setCategory(
                draft.category());

        habit.setDurationMinutes(
                draft.durationOrDefault());

        habit.setPriority(
                draft.priorityOrDefault());

        habit.setFlexibility(
                draft.flexibilityOrDefault());

        habit.setGoalId(
                draft.goalId());

        if (draft.trackingType() != null) {
            habit.setTrackingType(
                    draft.trackingType());
        }

        if (draft.startDate() != null) {
            habit.setStartDate(
                    draft.startDate());
        }

        habit.setEndDate(
                draft.endDate());

        HabitSchedule schedule =
                new HabitSchedule();

        schedule.setFrequencyType(
                draft.frequencyType() == null
                        ? Enums.FrequencyType.WEEKLY
                        : draft.frequencyType());

        schedule.setTimesPerPeriod(
                draft.occurrencesOrDefault());

        schedule.setDaysOfWeekMask(
                HabitSchedule.maskOf(
                        draft.preferredDays()));

        schedule.setPreferredTimeOfDay(
                draft.timeOfDayOrDefault());

        schedule.setPreferredStartMinute(
                draft.preferredStartMinute());

        habit.attachSchedule(schedule);

        return habits.save(habit);
    }

    public List<ScheduledItem> forDay(
            Long userId,
            LocalDate date) {

        List<ScheduledItem> items =
                new ArrayList<>(
                        scheduledItems
                                .findByUserIdAndScheduledDateOrderByStartMinuteAsc(
                                        userId,
                                        date));

        addTaskItems(
                items,
                userId,
                date,
                date);

        addPrayerItems(
                items,
                userId,
                date,
                date);

        items.sort(
                (a, b) ->
                        Integer.compare(
                                a.getStartMinute(),
                                b.getStartMinute()));

        return items;
    }

    public List<ScheduledItem> forRange(
            Long userId,
            LocalDate from,
            LocalDate to) {

        List<ScheduledItem> items =
                new ArrayList<>(
                        scheduledItems
                                .findByUserIdAndScheduledDateBetweenOrderByScheduledDateAscStartMinuteAsc(
                                        userId,
                                        from,
                                        to));

        addTaskItems(
                items,
                userId,
                from,
                to);

        addPrayerItems(
                items,
                userId,
                from,
                to);

        items.sort(
                (a, b) -> {

                    int dateCompare =
                            a.getScheduledDate()
                                    .compareTo(
                                            b.getScheduledDate());

                    if (dateCompare != 0) {
                        return dateCompare;
                    }

                    return Integer.compare(
                            a.getStartMinute(),
                            b.getStartMinute());
                });

        return items;
    }

    private void addTaskItems(
            List<ScheduledItem> items,
            Long userId,
            LocalDate from,
            LocalDate to) {

        List<Task> taskList =
                tasks.findByUserIdOrderByDueDateAscIdAsc(userId);

        for (Task task : taskList) {

            if (task.getScheduledDate() == null
                    || task.getStartMinute() == null) {
                continue;
            }

            if (task.getScheduledDate().isBefore(from)
                    || task.getScheduledDate().isAfter(to)) {
                continue;
            }

            int startMinute =
                    task.getStartMinute();

            int endMinute =
                    Math.min(
                            startMinute
                                    + task.getDurationMinutes(),
                            1439);

            items.add(
                    new ScheduledItem(
                            userId,
                            SourceType.TASK,
                            task.getId(),
                            task.getTitle(),
                            task.getScheduledDate(),
                            startMinute,
                            endMinute,
                            null));
        }
    }

    private void addPrayerItems(
            List<ScheduledItem> items,
            Long userId,
            LocalDate from,
            LocalDate to) {

        prayerSettings.findByUserId(userId)
                .ifPresent(setting -> {

                    LocalDate date = from;

                    while (!date.isAfter(to)) {

                        addPrayer(
                                items,
                                userId,
                                "Fajr",
                                date,
                                setting.getFajrTime());

                        addPrayer(
                                items,
                                userId,
                                "Dhuhr",
                                date,
                                setting.getDhuhrTime());

                        addPrayer(
                                items,
                                userId,
                                "Asr",
                                date,
                                setting.getAsrTime());

                        addPrayer(
                                items,
                                userId,
                                "Maghrib",
                                date,
                                setting.getMaghribTime());

                        addPrayer(
                                items,
                                userId,
                                "Isha",
                                date,
                                setting.getIshaTime());

                        date = date.plusDays(1);
                    }
                });
    }

    private void addPrayer(
            List<ScheduledItem> items,
            Long userId,
            String name,
            LocalDate date,
            LocalTime time) {

        if (time == null) {
            return;
        }

        int startMinute =
                time.getHour() * 60
                        + time.getMinute();

        int endMinute =
                Math.min(
                        startMinute + 20,
                        1439);

        long temporaryId =
                -Math.abs(
                        (long) date.hashCode()
                                * 100
                                + prayerNumber(name));

        items.add(
                new ScheduledItem(
                        temporaryId,
                        userId,
                        SourceType.PRAYER,
                        0L,
                        name,
                        date,
                        startMinute,
                        endMinute,
                        null));
    }

    private int prayerNumber(String name) {

        return switch (name) {
            case "Fajr" -> 1;
            case "Dhuhr" -> 2;
            case "Asr" -> 3;
            case "Maghrib" -> 4;
            case "Isha" -> 5;
            default -> 0;
        };
    }
    public List<Map<String, Object>> detectConflicts(
        Long userId,
        LocalDate date,
        Integer startMinute,
        Integer endMinute) {

    List<ScheduledItem> existing =
            scheduledItems
                    .findByUserIdAndScheduledDateOrderByStartMinuteAsc(
                            userId,
                            date);

    List<Map<String, Object>> conflicts = new ArrayList<>();

    for (ScheduledItem item : existing) {

        boolean overlaps =
                item.getStartMinute() < endMinute
                        && item.getEndMinute() > startMinute;

        if (!overlaps) {
            continue;
        }

        conflicts.add(
                Map.of(
                        "type", "TIME_OVERLAP",
                        "severity", "HIGH",
                        "date", date.toString(),
                        "existingItem", item.getDisplayName(),
                        "existingStartMinute", item.getStartMinute(),
                        "existingEndMinute", item.getEndMinute(),
                        "requestedStartMinute", startMinute,
                        "requestedEndMinute", endMinute,
                        "message",
                        "The requested time overlaps with "
                                + item.getDisplayName()
                                + "."));
    }
    return conflicts;
}
public PlannerDtos.ConflictResponse plannerConflicts(
        Long userId,
        LocalDate date,
        Integer startMinute,
        Integer endMinute) {

    List<PlannerDtos.ScheduledItemPayload> locked =
            new ArrayList<>();

    List<ScheduledItem> existing =
            scheduledItems
                    .findByUserIdAndScheduledDateOrderByStartMinuteAsc(
                            userId,
                            date);

    for (ScheduledItem item : existing) {
        locked.add(
                new PlannerDtos.ScheduledItemPayload(
                        item.getSourceType().name().toLowerCase()
                                + "-" + item.getSourceId(),
                        item.getDisplayName(),
                        item.getScheduledDate(),
                        item.getStartMinute(),
                        item.getEndMinute()));
    }

    addTaskLockedItems(
            locked,
            userId,
            date);

    addPrayerLockedItems(
            locked,
            userId,
            date);

    int duration = endMinute - startMinute;

    PlannerDtos.ActivityPayload requested =
            new PlannerDtos.ActivityPayload(
                    "requested-slot",
                    "Requested Slot",
                    duration,
                    1,
                    "TASK",
                    "HIGH",
                    "FIXED",
                    List.of(date.getDayOfWeek().name()),
                    List.of(),
                    null,
                    startMinute,
                    null,
                    null,
                    1,
                    date,
                    List.of());

    PlannerDtos.PlanPayload plan =
            new PlannerDtos.PlanPayload(
                    date,
                    1,
                    List.of(requested),
                    List.of(),
                    List.of(),
                    locked,
                    "SPREAD");

    return planner.conflicts(plan);
}
private void addTaskLockedItems(
        List<PlannerDtos.ScheduledItemPayload> locked,
        Long userId,
        LocalDate date) {

    tasks.findByUserIdOrderByDueDateAscIdAsc(userId)
            .forEach(task -> {

                if (task.getScheduledDate() == null
                        || task.getStartMinute() == null
                        || task.getDurationMinutes() == null) {
                    return;
                }

                if (!task.getScheduledDate().equals(date)) {
                    return;
                }

                int start = task.getStartMinute();

                int end =
                        Math.min(
                                start + task.getDurationMinutes(),
                                1439);

                locked.add(
                        new PlannerDtos.ScheduledItemPayload(
                                "task-" + task.getId(),
                                task.getTitle(),
                                date,
                                start,
                                end));
            });
}
private void addPrayerLockedItems(
        List<PlannerDtos.ScheduledItemPayload> locked,
        Long userId,
        LocalDate date) {

    prayerSettings.findByUserId(userId)
            .ifPresent(setting -> {

                addPrayerLocked(
                        locked,
                        "Fajr",
                        date,
                        setting.getFajrTime());

                addPrayerLocked(
                        locked,
                        "Dhuhr",
                        date,
                        setting.getDhuhrTime());

                addPrayerLocked(
                        locked,
                        "Asr",
                        date,
                        setting.getAsrTime());

                addPrayerLocked(
                        locked,
                        "Maghrib",
                        date,
                        setting.getMaghribTime());

                addPrayerLocked(
                        locked,
                        "Isha",
                        date,
                        setting.getIshaTime());
            });
}
private void addPrayerLocked(
        List<PlannerDtos.ScheduledItemPayload> locked,
        String name,
        LocalDate date,
        LocalTime time) {

    if (time == null) {
        return;
    }

    int start =
            time.getHour() * 60
                    + time.getMinute();

    int end =
            Math.min(start + 20, 1439);

    locked.add(
            new PlannerDtos.ScheduledItemPayload(
                    "prayer-" + name.toLowerCase(),
                    name,
                    date,
                    start,
                    end));
                }
        public PlannerDtos.PlanResponse whatIf(
        Long userId,
        PlannerDtos.WhatIfPayload payload) {

    return planner.whatIf(payload);
}
public PlannerDtos.PlanResponse propose(
        Long userId,
        PlannerDtos.PlanPayload payload) {

    return planner.propose(payload);
}
public PlannerDtos.PlanResponse rebalance(
        Long userId,
        PlannerDtos.RebalancePayload payload) {

    return planner.rebalance(payload);
}
}