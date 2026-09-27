package com.lifeplanner.prayer;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.lifeplanner.prayer.PrayerCompletionEntities.PrayerCompletion;
import com.lifeplanner.prayer.PrayerCompletionEntities.PrayerName;

@Repository
public interface PrayerCompletionRepository
        extends JpaRepository<PrayerCompletion, Long> {

    Optional<PrayerCompletion> findByUserIdAndPrayerDateAndPrayerName(
            Long userId,
            LocalDate prayerDate,
            PrayerName prayerName);

    List<PrayerCompletion> findByUserIdAndPrayerDateBetweenOrderByPrayerDateAsc(
            Long userId,
            LocalDate from,
            LocalDate to);
}