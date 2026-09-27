package com.lifeplanner.goal;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.lifeplanner.goal.MilestoneEntities.Milestone;
import com.lifeplanner.security.CurrentUser;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/goals/{goalId}/milestones")
@SecurityRequirement(name = "bearerAuth")
public class MilestoneController {

    private final MilestoneService milestoneService;
    private final CurrentUser currentUser;

    public MilestoneController(
            MilestoneService milestoneService,
            CurrentUser currentUser) {

        this.milestoneService = milestoneService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<Milestone> list(
            @PathVariable Long goalId) {

        return milestoneService.list(
                currentUser.requireId(),
                goalId);
    }

    @GetMapping("/{milestoneId}")
    public Milestone get(
            @PathVariable Long goalId,
            @PathVariable Long milestoneId) {

        return milestoneService.get(
                currentUser.requireId(),
                goalId,
                milestoneId);
    }

    @PostMapping
    public Milestone create(
            @PathVariable Long goalId,
            @RequestBody Milestone milestone) {

        return milestoneService.create(
                currentUser.requireId(),
                goalId,
                milestone);
    }

    @PutMapping("/{milestoneId}")
    public Milestone update(
            @PathVariable Long goalId,
            @PathVariable Long milestoneId,
            @RequestBody Milestone milestone) {

        return milestoneService.update(
                currentUser.requireId(),
                goalId,
                milestoneId,
                milestone);
    }

    @PostMapping("/{milestoneId}/complete")
    public Milestone complete(
            @PathVariable Long goalId,
            @PathVariable Long milestoneId) {

        return milestoneService.complete(
                currentUser.requireId(),
                goalId,
                milestoneId);
    }

    @PostMapping("/{milestoneId}/reopen")
    public Milestone reopen(
            @PathVariable Long goalId,
            @PathVariable Long milestoneId) {

        return milestoneService.reopen(
                currentUser.requireId(),
                goalId,
                milestoneId);
    }

    @DeleteMapping("/{milestoneId}")
    public void delete(
            @PathVariable Long goalId,
            @PathVariable Long milestoneId) {

        milestoneService.delete(
                currentUser.requireId(),
                goalId,
                milestoneId);
    }
}