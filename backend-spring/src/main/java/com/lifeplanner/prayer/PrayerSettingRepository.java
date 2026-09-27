package com.lifeplanner.prayer;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.lifeplanner.prayer.PrayerEntities.PrayerSetting;

@Repository
public interface PrayerSettingRepository extends JpaRepository<PrayerSetting, Long> {

    Optional<PrayerSetting> findByUserId(Long userId);
}