package com.lifeplanner.goal;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lifeplanner.common.ApiExceptions;
import com.lifeplanner.goal.MilestoneEntities.Milestone;

@Service
public class MilestoneService {

    private final MilestoneRepository repository;
    private final GoalRepository goals;

    public MilestoneService(
            MilestoneRepository repository,
            GoalRepository goals) {

        this.repository = repository;
        this.goals = goals;
    }

    public List<Milestone> list(
            Long userId,
            Long goalId) {

        requireGoal(userId, goalId);

        return repository.findByGoalIdOrderBySortOrderAscIdAsc(goalId);
    }

    public Milestone get(
            Long userId,
            Long goalId,
            Long milestoneId) {

        requireGoal(userId, goalId);

        return repository.findByIdAndGoalId(
                milestoneId,
                goalId)
                .orElseThrow(() ->
                        new ApiExceptions.NotFoundException(
                                "Milestone not found"));
    }

    @Transactional
    public Milestone create(
            Long userId,
            Long goalId,
            Milestone request) {

        requireGoal(userId, goalId);

        Milestone milestone =
                new Milestone(
                        goalId,
                        request.getTitle());

        milestone.setDueDate(
                request.getDueDate());

        milestone.setSortOrder(
                request.getSortOrder() == null
                        ? 0
                        : request.getSortOrder());

        return repository.save(milestone);
    }

    @Transactional
    public Milestone update(
            Long userId,
            Long goalId,
            Long milestoneId,
            Milestone request) {

        Milestone milestone =
                get(userId, goalId, milestoneId);

        milestone.setTitle(
                request.getTitle());

        milestone.setDueDate(
                request.getDueDate());

        milestone.setSortOrder(
                request.getSortOrder());

        return repository.save(milestone);
    }

    @Transactional
    public Milestone complete(
            Long userId,
            Long goalId,
            Long milestoneId) {

        Milestone milestone =
                get(userId, goalId, milestoneId);

        milestone.setCompletedAt(
                Instant.now());

        return repository.save(milestone);
    }

    @Transactional
    public Milestone reopen(
            Long userId,
            Long goalId,
            Long milestoneId) {

        Milestone milestone =
                get(userId, goalId, milestoneId);

        milestone.setCompletedAt(null);

        return repository.save(milestone);
    }

    @Transactional
    public void delete(
            Long userId,
            Long goalId,
            Long milestoneId) {

        Milestone milestone =
                get(userId, goalId, milestoneId);

        repository.delete(milestone);
    }

    private void requireGoal(
            Long userId,
            Long goalId) {

        goals.findByIdAndUserId(
                goalId,
                userId)
                .orElseThrow(() ->
                        new ApiExceptions.NotFoundException(
                                "Goal not found"));
    }
}