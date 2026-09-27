package com.lifeplanner.goal;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lifeplanner.common.ApiExceptions;
import com.lifeplanner.goal.GoalEntities.Goal;
import com.lifeplanner.goal.GoalEntities.GoalStatus;

@Service
public class GoalService {

    private final GoalRepository repository;

    public GoalService(GoalRepository repository) {
        this.repository = repository;
    }

    public List<Goal> list(Long userId) {
        return repository.findByUserIdOrderByTargetDateAscIdAsc(userId);
    }

    public Goal get(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() ->
                        new ApiExceptions.NotFoundException(
                                "Goal not found"));
    }

    @Transactional
    public Goal create(Long userId, Goal goal) {

        goal.setUserId(userId);
        goal.setStatus(GoalStatus.ACTIVE);

        if (goal.getTargetCount() == null
                || goal.getTargetCount() < 1) {
            goal.setTargetCount(1);
        }

        goal.setAchievedCount(0);

        return repository.save(goal);
    }

    @Transactional
    public Goal update(
            Long userId,
            Long id,
            Goal request) {

        Goal goal = get(userId, id);

        goal.setTitle(request.getTitle());
        goal.setDescription(request.getDescription());
        goal.setCategory(request.getCategory());
        goal.setTargetYear(request.getTargetYear());
        goal.setTargetDate(request.getTargetDate());
        goal.setTargetCount(request.getTargetCount());

        return repository.save(goal);
    }

    @Transactional
    public Goal updateStatus(
            Long userId,
            Long id,
            GoalStatus status) {

        Goal goal = get(userId, id);

        goal.setStatus(status);

        return repository.save(goal);
    }

    @Transactional
    public Goal updateProgress(
            Long userId,
            Long id,
            Integer achievedCount) {

        Goal goal = get(userId, id);

        if (achievedCount == null || achievedCount < 0) {
            throw new IllegalArgumentException(
                    "Achieved count cannot be negative");
        }

        goal.setAchievedCount(achievedCount);

        if (achievedCount >= goal.getTargetCount()) {
            goal.setStatus(GoalStatus.ACHIEVED);
        }

        return repository.save(goal);
    }

    @Transactional
    public void delete(Long userId, Long id) {

        Goal goal = get(userId, id);

        repository.delete(goal);
    }
}