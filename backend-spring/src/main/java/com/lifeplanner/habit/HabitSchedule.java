package com.lifeplanner.habit;

import jakarta.persistence.*;
/**
 * The recurrence RULE for a habit.
 *
 * One row per habit. Concrete occurrences are derived when a date range is
 * requested, so "read every day" does not become 365 rows a year.
 */
@Entity
@Table(name = "habit_schedule")
public class HabitSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "habit_id", nullable = false, unique = true)
    private Habit habit;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency_type", nullable = false, length = 12)
    private Enums.FrequencyType frequencyType = Enums.FrequencyType.WEEKLY;

    @Column(name = "times_per_period", nullable = false)
    private Integer timesPerPeriod = 1;

    @Column(name = "interval_days")
    private Integer intervalDays;

    /** Monday = bit 0 ... Sunday = bit 6. Null means "any day". */
    @Column(name = "days_of_week_mask")
    private Short daysOfWeekMask;

    @Column(name = "day_of_month")
    private Short dayOfMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_time_of_day", nullable = false, length = 10)
    private Enums.TimeOfDay preferredTimeOfDay = Enums.TimeOfDay.ANY;

    @Column(name = "preferred_start_minute")
    private Integer preferredStartMinute;

    @Column(name = "earliest_minute")
    private Integer earliestMinute;

    @Column(name = "latest_minute")
    private Integer latestMinute;

    public Long getId() { return id; }
    public Habit getHabit() { return habit; }
    public Enums.FrequencyType getFrequencyType() { return frequencyType; }
    public Integer getTimesPerPeriod() { return timesPerPeriod; }
    public Integer getIntervalDays() { return intervalDays; }
    public Short getDaysOfWeekMask() { return daysOfWeekMask; }
    public Short getDayOfMonth() { return dayOfMonth; }
    public Enums.TimeOfDay getPreferredTimeOfDay() { return preferredTimeOfDay; }
    public Integer getPreferredStartMinute() { return preferredStartMinute; }
    public Integer getEarliestMinute() { return earliestMinute; }
    public Integer getLatestMinute() { return latestMinute; }

    public void setHabit(Habit habit) { this.habit = habit; }
    public void setFrequencyType(Enums.FrequencyType f) { this.frequencyType = f; }
    public void setTimesPerPeriod(Integer t) { this.timesPerPeriod = t; }
    public void setIntervalDays(Integer i) { this.intervalDays = i; }
    public void setDaysOfWeekMask(Short m) { this.daysOfWeekMask = m; }
    public void setDayOfMonth(Short d) { this.dayOfMonth = d; }
    public void setPreferredTimeOfDay(Enums.TimeOfDay t) { this.preferredTimeOfDay = t; }
    public void setPreferredStartMinute(Integer m) { this.preferredStartMinute = m; }
    public void setEarliestMinute(Integer m) { this.earliestMinute = m; }
    public void setLatestMinute(Integer m) { this.latestMinute = m; }

    /** Converts the bitmask to the day names the planner service expects. */
    public java.util.List<String> dayNames() {
        String[] names = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY",
                          "FRIDAY", "SATURDAY", "SUNDAY"};
        java.util.List<String> result = new java.util.ArrayList<>();
        if (daysOfWeekMask == null) {
            return result;
        }
        for (int bit = 0; bit < 7; bit++) {
            if ((daysOfWeekMask & (1 << bit)) != 0) {
                result.add(names[bit]);
            }
        }
        return result;
    }

    public static Short maskOf(java.util.List<String> dayNames) {
        if (dayNames == null || dayNames.isEmpty()) {
            return null;
        }
        java.util.List<String> names = java.util.List.of("MONDAY", "TUESDAY",
                "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");
        int mask = 0;
        for (String day : dayNames) {
            int index = names.indexOf(day.toUpperCase());
            if (index >= 0) {
                mask |= (1 << index);
            }
        }
        return (short) mask;
    }
}
