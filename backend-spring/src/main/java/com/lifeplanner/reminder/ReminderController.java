package com.lifeplanner.reminder;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

import com.lifeplanner.security.CurrentUser;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/reminders")
@SecurityRequirement(name = "bearerAuth")
public class ReminderController {

    private final ReminderService reminders;
    private final ReminderDueService reminderDueService;
    private final CurrentUser currentUser;

    public ReminderController(
            ReminderService reminders,
            ReminderDueService reminderDueService,
            CurrentUser currentUser) {

        this.reminders = reminders;
        this.reminderDueService = reminderDueService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<Reminder> list() {

        return reminders.list(
                currentUser.requireId());
    }

    @PostMapping
    public Reminder create(
            @RequestBody CreateReminderRequest request) {

        return reminders.create(
                currentUser.requireId(),
                request.sourceType(),
                request.sourceId(),
                request.remindMinute(),
                request.leadMinutes(),
                request.channel());
    }

    @PatchMapping("/{id}/enabled")
    public Reminder updateEnabled(
            @PathVariable Long id,
            @RequestBody EnabledRequest request) {

        return reminders.updateEnabled(
                currentUser.requireId(),
                id,
                request.enabled());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(
            @PathVariable Long id) {

        return reminders.delete(
                currentUser.requireId(),
                id);
    }

    @GetMapping("/due")
    public List<ReminderDueService.DueReminder> dueNow(
            @RequestParam String date,
            @RequestParam int currentMinute) {

        return reminderDueService.dueNow(
                currentUser.requireId(),
                java.time.LocalDate.parse(date),
                currentMinute);
    }

    public record CreateReminderRequest(
            Reminder.SourceType sourceType,
            Long sourceId,
            Short remindMinute,
            Short leadMinutes,
            Reminder.Channel channel) {}

    public record EnabledRequest(
            boolean enabled) {}
}