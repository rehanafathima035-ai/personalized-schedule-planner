package com.lifeplanner.schedule;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.lifeplanner.schedule.ScheduleEntities.ScheduledItem;
import com.lifeplanner.schedule.ScheduleEntities.SourceType;

@Repository
public interface ScheduledItemRepository extends JpaRepository<ScheduledItem, Long> {

    List<ScheduledItem> findByUserIdAndScheduledDateBetweenOrderByScheduledDateAscStartMinuteAsc(
            Long userId, LocalDate from, LocalDate to);

    List<ScheduledItem> findByUserIdAndScheduledDateOrderByStartMinuteAsc(
            Long userId, LocalDate date);

    List<ScheduledItem> findByUserIdAndSourceTypeAndSourceIdAndScheduledDateBetweenOrderByScheduledDateAscStartMinuteAsc(
            Long userId,
            SourceType sourceType,
            Long sourceId,
            LocalDate from,
            LocalDate to);

    Optional<ScheduledItem> findByIdAndUserId(Long id, Long userId);

    void deleteByUserIdAndSourceTypeAndSourceId(
            Long userId,
            SourceType sourceType,
            Long sourceId);
}