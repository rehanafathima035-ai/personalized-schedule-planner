package com.lifeplanner.habit;

/** Shared vocabulary between the database, the API and the planner service. */
public final class Enums {

    private Enums() {}

    public enum TrackingType { CHECKBOX, NUMERIC, DURATION, COUNT, YES_NO }

    public enum Priority { LOW, MEDIUM, HIGH, CRITICAL }

    public enum Flexibility { FIXED, PREFERRED, FLEXIBLE }

    public enum HabitStatus { ACTIVE, PAUSED, ARCHIVED }

    public enum FrequencyType { DAILY, WEEKLY, MONTHLY, YEARLY, INTERVAL, CUSTOM }

    public enum TimeOfDay { ANY, MORNING, AFTERNOON, EVENING, NIGHT }
}
