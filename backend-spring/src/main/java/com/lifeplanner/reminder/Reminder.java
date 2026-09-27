package com.lifeplanner.reminder;

import jakarta.persistence.*;

@Entity
@Table(name = "reminder")
public class Reminder {

    public enum SourceType {
        HABIT,
        TASK,
        PRAYER,
        GOAL
    }

    public enum Channel {
        IN_APP,
        EMAIL,
        PUSH
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SourceType sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "remind_minute")
    private Short remindMinute;

    @Column(name = "lead_minutes", nullable = false)
    private Short leadMinutes = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private Channel channel = Channel.IN_APP;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    public Reminder() {
    }

    public Reminder(
            Long userId,
            SourceType sourceType,
            Long sourceId,
            Short remindMinute,
            Short leadMinutes,
            Channel channel) {

        this.userId = userId;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.remindMinute = remindMinute;
        this.leadMinutes = leadMinutes;
        this.channel = channel;
        this.enabled = true;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public Short getRemindMinute() {
        return remindMinute;
    }

    public Short getLeadMinutes() {
        return leadMinutes;
    }

    public Channel getChannel() {
        return channel;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setRemindMinute(Short remindMinute) {
        this.remindMinute = remindMinute;
    }

    public void setLeadMinutes(Short leadMinutes) {
        this.leadMinutes = leadMinutes;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}