package com.lifeplanner.schedule;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.lifeplanner.schedule.ScheduleEntities.ScheduleProposal;

@Repository
public interface ProposalRepository extends JpaRepository<ScheduleProposal, Long> {
    Optional<ScheduleProposal> findByIdAndUserId(Long id, Long userId);

    List<ScheduleProposal> findByUserIdAndStatusOrderByCreatedAtDesc(
            Long userId, ScheduleEntities.ProposalStatus status);
}