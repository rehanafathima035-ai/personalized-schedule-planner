package com.lifeplanner.reminder;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lifeplanner.common.ApiExceptions;

@Service
public class ReminderService {

    private final ReminderRepository reminders;

    public ReminderService(ReminderRepository reminders) {
        this.reminders = reminders;
    }

    public List<Reminder> list(Long userId) {
        return reminders.findByUserIdOrderByIdDesc(userId);
    }

    @Transactional
    public Reminder create(
            Long userId,
            Reminder.SourceType sourceType,
            Long sourceId,
            Short remindMinute,
            Short leadMinutes,
            Reminder.Channel channel) {

        if (sourceType == null) {
            throw new IllegalArgumentException(
                    "Reminder source type is required.");
        }

        if (leadMinutes == null || leadMinutes < 0) {
            throw new IllegalArgumentException(
                    "Lead minutes cannot be negative.");
        }

        if (remindMinute != null
                && (remindMinute < 0 || remindMinute > 1439)) {
            throw new IllegalArgumentException(
                    "Reminder time must be between 0 and 1439 minutes.");
        }

        Reminder reminder =
                new Reminder(
                        userId,
                        sourceType,
                        sourceId,
                        remindMinute,
                        leadMinutes,
                        channel == null
                                ? Reminder.Channel.IN_APP
                                : channel);

        return reminders.save(reminder);
    }

    @Transactional
    public Reminder updateEnabled(
            Long userId,
            Long reminderId,
            boolean enabled) {

        Reminder reminder =
                reminders.findByIdAndUserId(
                        reminderId,
                        userId)
                        .orElseThrow(() ->
                                new ApiExceptions.NotFoundException(
                                        "That reminder does not exist."));

        reminder.setEnabled(enabled);

        return reminders.save(reminder);
    }

    @Transactional
    public Map<String, Object> delete(
            Long userId,
            Long reminderId) {

        Reminder reminder =
                reminders.findByIdAndUserId(
                        reminderId,
                        userId)
                        .orElseThrow(() ->
                                new ApiExceptions.NotFoundException(
                                        "That reminder does not exist."));

        reminders.delete(reminder);

        return Map.of(
                "status", "DELETED",
                "id", reminderId);
    }
}