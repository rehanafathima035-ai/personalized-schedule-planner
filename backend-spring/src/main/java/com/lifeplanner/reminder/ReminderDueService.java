package com.lifeplanner.reminder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class ReminderDueService {

    private final ReminderRepository reminders;

    public ReminderDueService(ReminderRepository reminders) {
        this.reminders = reminders;
    }

    public List<DueReminder> dueNow(
            Long userId,
            LocalDate date,
            int currentMinute) {

        List<DueReminder> result = new ArrayList<>();

        for (Reminder reminder :
                reminders.findByUserIdAndEnabledTrue(userId)) {

            Short remindMinute =
                    reminder.getRemindMinute();

            if (remindMinute == null) {
                continue;
            }

            int triggerMinute =
                    remindMinute.intValue()
                    - reminder.getLeadMinutes().intValue();

            if (triggerMinute == currentMinute) {

                result.add(
                        new DueReminder(
                                reminder.getId(),
                                reminder.getSourceType(),
                                reminder.getSourceId(),
                                date,
                                LocalTime.of(
                                        triggerMinute / 60,
                                        triggerMinute % 60),
                                reminder.getChannel()));
            }
        }

        return result;
    }

    public record DueReminder(
            Long reminderId,
            Reminder.SourceType sourceType,
            Long sourceId,
            LocalDate date,
            LocalTime triggerTime,
            Reminder.Channel channel) {}
}