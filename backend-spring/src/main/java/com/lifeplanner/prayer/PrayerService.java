package com.lifeplanner.prayer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lifeplanner.common.ApiExceptions;
import com.lifeplanner.prayer.PrayerDtos.PrayerSettingRequest;
import com.lifeplanner.prayer.PrayerDtos.PrayerSettingResponse;
import com.lifeplanner.prayer.PrayerEntities.PrayerSetting;

@Service
public class PrayerService {

    private final PrayerSettingRepository repository;

    public PrayerService(PrayerSettingRepository repository) {
        this.repository = repository;
    }

    public PrayerSettingResponse get(Long userId) {
        PrayerSetting setting = repository.findByUserId(userId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException(
                        "Prayer times have not been configured yet."));

        return toResponse(setting);
    }

    @Transactional
    public PrayerSettingResponse save(
            Long userId,
            PrayerSettingRequest request) {

        if (request.fajrTime() == null ||
                request.dhuhrTime() == null ||
                request.asrTime() == null ||
                request.maghribTime() == null ||
                request.ishaTime() == null) {

            throw new ApiExceptions.ConflictException(
                    "All five prayer times are required.");
        }

        PrayerSetting setting = repository.findByUserId(userId)
                .orElse(new PrayerSetting());

        if (setting.getUserId() == null) {
            setting = new PrayerSetting(
                    userId,
                    request.fajrTime(),
                    request.dhuhrTime(),
                    request.asrTime(),
                    request.maghribTime(),
                    request.ishaTime());
        } else {
            setting.setFajrTime(request.fajrTime());
            setting.setDhuhrTime(request.dhuhrTime());
            setting.setAsrTime(request.asrTime());
            setting.setMaghribTime(request.maghribTime());
            setting.setIshaTime(request.ishaTime());
        }

        return toResponse(repository.save(setting));
    }

    private PrayerSettingResponse toResponse(PrayerSetting setting) {
        return new PrayerSettingResponse(
                setting.getFajrTime(),
                setting.getDhuhrTime(),
                setting.getAsrTime(),
                setting.getMaghribTime(),
                setting.getIshaTime());
    }
}