package com.lifeplanner.habit;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.*;

@Entity
@Table(name = "habit")
public class Habit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Ownership is stored on the row itself so every query can filter by it. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "goal_id")
    private Long goalId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 60)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "tracking_type", nullable = false, length = 20)
    private Enums.TrackingType trackingType = Enums.TrackingType.CHECKBOX;

    @Column(name = "target_value", precision = 10, scale = 2)
    private BigDecimal targetValue;

    @Column(name = "target_unit", length = 24)
    private String targetUnit;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes = 30;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Enums.Priority priority = Enums.Priority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Enums.Flexibility flexibility = Enums.Flexibility.FLEXIBLE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Enums.HabitStatus status = Enums.HabitStatus.ACTIVE;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate = LocalDate.now();

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "is_demo", nullable = false)
    private boolean demo = false;

    @OneToOne(mappedBy = "habit", cascade = CascadeType.ALL, orphanRemoval = true)
    private HabitSchedule schedule;

    protected Habit() {}

    public Habit(Long userId, String name) {
        this.userId = userId;
        this.name = name;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getGoalId() { return goalId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public Enums.TrackingType getTrackingType() { return trackingType; }
    public BigDecimal getTargetValue() { return targetValue; }
    public String getTargetUnit() { return targetUnit; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public Enums.Priority getPriority() { return priority; }
    public Enums.Flexibility getFlexibility() { return flexibility; }
    public Enums.HabitStatus getStatus() { return status; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public boolean isDemo() { return demo; }
    public HabitSchedule getSchedule() { return schedule; }

    public void setGoalId(Long goalId) { this.goalId = goalId; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setCategory(String category) { this.category = category; }
    public void setTrackingType(Enums.TrackingType t) { this.trackingType = t; }
    public void setTargetValue(BigDecimal v) { this.targetValue = v; }
    public void setTargetUnit(String u) { this.targetUnit = u; }
    public void setDurationMinutes(Integer m) { this.durationMinutes = m; }
    public void setPriority(Enums.Priority p) { this.priority = p; }
    public void setFlexibility(Enums.Flexibility f) { this.flexibility = f; }
    public void setStatus(Enums.HabitStatus s) { this.status = s; }
    public void setStartDate(LocalDate d) { this.startDate = d; }
    public void setEndDate(LocalDate d) { this.endDate = d; }

    public void attachSchedule(HabitSchedule schedule) {
        this.schedule = schedule;
        schedule.setHabit(this);
    }
}
