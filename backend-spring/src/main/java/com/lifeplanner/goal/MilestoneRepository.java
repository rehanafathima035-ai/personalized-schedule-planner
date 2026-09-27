package com.lifeplanner.goal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lifeplanner.goal.MilestoneEntities.Milestone;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {

    List<Milestone> findByGoalIdOrderBySortOrderAscIdAsc(Long goalId);

    Optional<Milestone> findByIdAndGoalId(Long id, Long goalId);
}