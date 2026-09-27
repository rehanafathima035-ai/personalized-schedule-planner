package com.lifeplanner.prayer;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lifeplanner.common.ApiExceptions;
import com.lifeplanner.prayer.PrayerCompletionEntities.PrayerCompletion;
import com.lifeplanner.prayer.PrayerCompletionEntities.PrayerName;
import com.lifeplanner.prayer.PrayerCompletionEntities.PrayerCompletionStatus;

@Service
public class PrayerCompletionService {

    private final PrayerCompletionRepository repository;

    public PrayerCompletionService(PrayerCompletionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PrayerCompletion complete(
            Long userId,
            LocalDate date,
            PrayerName prayerName) {

        if (prayerName == null) {
            throw new ApiExceptions.ConflictException(
                    "Prayer name is required.");
        }

        PrayerCompletion completion =
                repository
                        .findByUserIdAndPrayerDateAndPrayerName(
                                userId,
                                date,
                                prayerName)
                        .orElse(null);

        if (completion == null) {
            completion = new PrayerCompletion(
                    userId,
                    date,
                    prayerName);
        } else {
            completion.setStatus(
                    PrayerCompletionStatus.COMPLETED);
        }

        return repository.save(completion);
    }

    @Transactional
    public void uncomplete(
            Long userId,
            LocalDate date,
            PrayerName prayerName) {

        repository
                .findByUserIdAndPrayerDateAndPrayerName(
                        userId,
                        date,
                        prayerName)
                .ifPresent(repository::delete);
    }

    public List<PrayerCompletion> forRange(
            Long userId,
            LocalDate from,
            LocalDate to) {

        return repository
                .findByUserIdAndPrayerDateBetweenOrderByPrayerDateAsc(
                        userId,
                        from,
                        to);
    }
}