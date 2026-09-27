package com.lifeplanner.goal;

import java.util.List;

import org.springframework.stereotype.Service;

import com.lifeplanner.common.ApiExceptions;
import com.lifeplanner.goal.GoalEntities.Goal;
import com.lifeplanner.goal.MilestoneEntities.Milestone;
import com.lifeplanner.habit.HabitRepository;
import com.lifeplanner.task.TaskEntities.TaskStatus;
import com.lifeplanner.task.TaskRepository;

@Service
public class GoalProgressService {

    private final GoalRepository goals;
    private final MilestoneRepository milestones;
    private final HabitRepository habits;
    private final TaskRepository tasks;

    public GoalProgressService(
            GoalRepository goals,
            MilestoneRepository milestones,
            HabitRepository habits,
            TaskRepository tasks) {

        this.goals = goals;
        this.milestones = milestones;
        this.habits = habits;
        this.tasks = tasks;
    }

    public GoalProgress get(
            Long userId,
            Long goalId) {

        Goal goal =
                goals.findByIdAndUserId(
                        goalId,
                        userId)
                .orElseThrow(() ->
                        new ApiExceptions.NotFoundException(
                                "Goal not found"));

        long habitCount =
                habits.countByUserIdAndGoalId(
                        userId,
                        goalId);

        long taskCount =
                tasks.countByUserIdAndGoalId(
                        userId,
                        goalId);

        long completedTasks =
                tasks.countByUserIdAndGoalIdAndStatus(
                        userId,
                        goalId,
                        TaskStatus.COMPLETED);

        List<Milestone> milestoneList =
                milestones.findByGoalIdOrderBySortOrderAscIdAsc(
                        goalId);

        long completedMilestones =
                milestoneList.stream()
                        .filter(m -> m.getCompletedAt() != null)
                        .count();

        double milestonePercentage =
                milestoneList.isEmpty()
                        ? 0.0
                        : completedMilestones * 100.0
                                / milestoneList.size();

        double taskPercentage =
                taskCount == 0
                        ? 0.0
                        : completedTasks * 100.0
                                / taskCount;

        return new GoalProgress(
                goal.getId(),
                goal.getTitle(),
                goal.getStatus().name(),
                goal.getTargetCount(),
                goal.getAchievedCount(),
                habitCount,
                taskCount,
                completedTasks,
                milestoneList.size(),
                completedMilestones,
                Math.round(milestonePercentage * 100.0) / 100.0,
                Math.round(taskPercentage * 100.0) / 100.0);
    }

    public record GoalProgress(
            Long goalId,
            String goalTitle,
            String status,
            Integer targetCount,
            Integer achievedCount,
            long habitCount,
            long taskCount,
            long completedTasks,
            long milestoneCount,
            long completedMilestones,
            double milestonePercentage,
            double taskPercentage) {
    }
}