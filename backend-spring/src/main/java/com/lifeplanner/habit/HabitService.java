package com.lifeplanner.habit;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.lifeplanner.task.TaskRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeplanner.common.ApiExceptions;
import com.lifeplanner.planner.PlannerClient;
import com.lifeplanner.planner.PlannerDtos;
import com.lifeplanner.prayer.PrayerSettingRepository;
import com.lifeplanner.schedule.ProposalRepository;
import com.lifeplanner.schedule.ScheduleEntities.ProposalOrigin;
import com.lifeplanner.schedule.ScheduleEntities.ScheduleConflict;
import com.lifeplanner.schedule.ScheduleEntities.ScheduleProposal;
import com.lifeplanner.schedule.ScheduleEntities.ScheduleProposalItem;
import com.lifeplanner.schedule.ScheduledItemRepository;

@Service
public class HabitService {

    private final HabitRepository habits;
    private final ProposalRepository proposals;
    private final ScheduledItemRepository scheduledItems;
    private final PlannerClient planner;
    private final ObjectMapper objectMapper;
    private final PrayerSettingRepository prayerSettings;
    private final TaskRepository tasks;

    public HabitService(
            HabitRepository habits,
            ProposalRepository proposals,
            ScheduledItemRepository scheduledItems,
            PlannerClient planner,
            ObjectMapper objectMapper,
            PrayerSettingRepository prayerSettings,
                TaskRepository tasks) {
        this.habits = habits;
        this.proposals = proposals;
        this.scheduledItems = scheduledItems;
        this.planner = planner;
        this.objectMapper = objectMapper;
        this.prayerSettings = prayerSettings;
        this.tasks = tasks;
    }

    public List<HabitDtos.HabitSummary> list(Long userId) {
        return habits.findByUserIdAndStatus(
                userId,
                Enums.HabitStatus.ACTIVE)
                .stream()
                .map(HabitDtos.HabitSummary::of)
                .toList();
    }

    public HabitDtos.HabitSummary get(Long userId, Long habitId) {
        return HabitDtos.HabitSummary.of(
                requireOwned(userId, habitId));
    }

    public Habit requireOwned(Long userId, Long habitId) {
        return habits.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException(
                        "That habit does not exist."));
    }

    public Map<String, Object> interpret(String text) {
        PlannerDtos.InterpretResponse response =
                planner.interpret(text);

        return Map.of(
                "interpretation", response.interpretation(),
                "requiresClarification",
                response.requires_clarification(),
                "message",
                "Nothing has been saved. Review and approve to continue.");
    }

    @Transactional
    public ProposalView preview(
            Long userId,
            HabitDtos.PreviewRequest request) {

        HabitDtos.HabitDraft draft = request.draft();

        if (draft == null) {
            throw new ApiExceptions.ConflictException(
                    "Nothing to preview: provide a habit draft.");
        }

        if (habits.existsByUserIdAndNameIgnoreCaseAndStatus(
                userId,
                draft.name(),
                Enums.HabitStatus.ACTIVE)) {

            throw new ApiExceptions.ConflictException(
                    "You already have an active habit called \""
                            + draft.name() + "\".");
        }

        LocalDate start = request.horizonStart() == null
                ? LocalDate.now()
                : request.horizonStart();

        int days = request.horizonDays() == null
                ? 7
                : request.horizonDays();

        PlannerDtos.PlanPayload payload =
                buildPlanPayload(userId, draft, start, days);

        PlannerDtos.PlanResponse response =
                planner.propose(payload);

        ScheduleProposal proposal =
                new ScheduleProposal(
                        userId,
                        ProposalOrigin.NEW_HABIT,
                        start,
                        days);

        proposal.setStrategy(
                response.proposal().strategy());

        proposal.setScore(
                response.proposal().score());

        proposal.setExplanationJson(
                writeJson(response.proposal().explanations()));

        proposal.setDraftJson(
                writeJson(draft));

        for (PlannerDtos.ProposalItem item :
                response.proposal().items()) {

            ScheduleProposalItem row =
                    new ScheduleProposalItem(
                            item.activity_name(),
                            item.day(),
                            item.start_minute(),
                            item.end_minute());

            row.setReasonCodes(
                    writeJson(item.reason_codes()));

            proposal.addItem(row);
        }

        for (PlannerDtos.ProposalConflict conflict :
                response.proposal().conflicts()) {

            proposal.addConflict(
                    new ScheduleConflict(
                            conflict.type(),
                            conflict.severity(),
                            conflict.message(),
                            conflict.day()));
        }

        proposals.save(proposal);

        return new ProposalView(
                proposal,
                response);
    }

    private PlannerDtos.PlanPayload buildPlanPayload(
            Long userId,
            HabitDtos.HabitDraft draft,
            LocalDate start,
            int days) {

        PlannerDtos.ActivityPayload activity =
                new PlannerDtos.ActivityPayload(
                        "draft",
                        draft.name(),
                        draft.durationOrDefault(),
                        draft.occurrencesOrDefault(),
                        "HABIT",
                        draft.priorityOrDefault().name(),
                        draft.flexibilityOrDefault().name(),
                        draft.preferredDays() == null
                                ? List.of()
                                : draft.preferredDays(),
                        draft.excludedDays() == null
                                ? List.of()
                                : draft.excludedDays(),
                        draft.timeOfDayOrDefault().name(),
                        draft.preferredStartMinute(),
                        draft.minSpacingDays(),
                        draft.maxSpacingDays(),
                        1,
                        null,
                        List.of());

        List<PlannerDtos.ScheduledItemPayload> locked =
                new ArrayList<>();

        scheduledItems
                .findByUserIdAndScheduledDateBetweenOrderByScheduledDateAscStartMinuteAsc(
                        userId,
                        start,
                        start.plusDays(days - 1L))
                .forEach(item ->
                        locked.add(
                                new PlannerDtos.ScheduledItemPayload(
                                        "existing-" + item.getId(),
                                        item.getDisplayName(),
                                        item.getScheduledDate(),
                                        item.getStartMinute(),
                                        item.getEndMinute())));
        
        tasks.findByUserIdOrderByDueDateAscIdAsc(userId)
        .forEach(task -> {

            if (task.getScheduledDate() == null
                    || task.getStartMinute() == null
                    || task.getDurationMinutes() == null) {
                return;
            }

            if (task.getScheduledDate().isBefore(start)
                    || task.getScheduledDate()
                            .isAfter(start.plusDays(days - 1L))) {
                return;
            }

            int startMinute =
                    task.getStartMinute();

            int endMinute =
                    Math.min(
                            startMinute + task.getDurationMinutes(),
                            1439);

            locked.add(
                    new PlannerDtos.ScheduledItemPayload(
                            "task-" + task.getId(),
                            task.getTitle(),
                            task.getScheduledDate(),
                            startMinute,
                            endMinute));
        });                               
        prayerSettings.findByUserId(userId)
                .ifPresent(setting -> {

                    for (int i = 0; i < days; i++) {

                        LocalDate date =
                                start.plusDays(i);

                        addPrayer(
                                locked,
                                "Fajr",
                                date,
                                setting.getFajrTime());

                        addPrayer(
                                locked,
                                "Dhuhr",
                                date,
                                setting.getDhuhrTime());

                        addPrayer(
                                locked,
                                "Asr",
                                date,
                                setting.getAsrTime());

                        addPrayer(
                                locked,
                                "Maghrib",
                                date,
                                setting.getMaghribTime());

                        addPrayer(
                                locked,
                                "Isha",
                                date,
                                setting.getIshaTime());
                    }
                });

        return new PlannerDtos.PlanPayload(
                start,
                days,
                List.of(activity),
                List.of(),
                List.of(),
                locked,
                "SPREAD");
    }

    private void addPrayer(
            List<PlannerDtos.ScheduledItemPayload> locked,
            String name,
            LocalDate date,
            LocalTime time) {

        if (time == null) {
            return;
        }

        int startMinute =
                time.getHour() * 60 + time.getMinute();

        int endMinute =
                Math.min(startMinute + 20, 1439);

        locked.add(
                new PlannerDtos.ScheduledItemPayload(
                        "prayer-" +
                                name.toLowerCase() +
                                "-" +
                                date,
                        name,
                        date,
                        startMinute,
                        endMinute));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Could not serialize proposal data",
                    ex);
        }
    }

    public record ProposalView(
            ScheduleProposal proposal,
            PlannerDtos.PlanResponse response) {}
}