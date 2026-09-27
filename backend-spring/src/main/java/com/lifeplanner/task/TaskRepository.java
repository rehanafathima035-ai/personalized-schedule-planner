package com.lifeplanner.task;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lifeplanner.task.TaskEntities.Task;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByUserIdOrderByDueDateAscIdAsc(Long userId);

    Optional<Task> findByIdAndUserId(Long id, Long userId);
    long countByUserIdAndGoalId(Long userId, Long goalId);


    long countByUserIdAndGoalIdAndStatus(
            Long userId,
            Long goalId,
            TaskEntities.TaskStatus status);

    long countByUserIdAndMilestoneIdAndStatus(
            Long userId,
            Long milestoneId,
            TaskEntities.TaskStatus status);
    }