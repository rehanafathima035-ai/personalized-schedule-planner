package com.lifeplanner.goal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lifeplanner.goal.GoalEntities.Goal;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    List<Goal> findByUserIdOrderByTargetDateAscIdAsc(Long userId);

    Optional<Goal> findByIdAndUserId(Long id, Long userId);
}