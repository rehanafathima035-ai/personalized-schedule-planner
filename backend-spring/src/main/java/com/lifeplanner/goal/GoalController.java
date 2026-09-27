package com.lifeplanner.goal;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.lifeplanner.goal.GoalEntities.Goal;
import com.lifeplanner.goal.GoalEntities.GoalStatus;
import com.lifeplanner.security.CurrentUser;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/goals")
@SecurityRequirement(name = "bearerAuth")
public class GoalController {

    private final GoalService goalService;
    private final CurrentUser currentUser;
    private final GoalProgressService goalProgressService;
    public GoalController(
            GoalService goalService,
            GoalProgressService goalProgressService,
            CurrentUser currentUser) {

        this.goalService = goalService;
        this.currentUser = currentUser;
        this.goalProgressService = goalProgressService;
    }
    @GetMapping("/{id}/progress")
    public GoalProgressService.GoalProgress progress(
            @PathVariable Long id) {

        return goalProgressService.get(
                currentUser.requireId(),
                id);
    }
    @GetMapping
    public List<Goal> list() {
        return goalService.list(
                currentUser.requireId());
    }

    @GetMapping("/{id}")
    public Goal get(@PathVariable Long id) {
        return goalService.get(
                currentUser.requireId(),
                id);
    }

    @PostMapping
    public Goal create(@RequestBody Goal goal) {
        return goalService.create(
                currentUser.requireId(),
                goal);
    }

    @PutMapping("/{id}")
    public Goal update(
            @PathVariable Long id,
            @RequestBody Goal goal) {

        return goalService.update(
                currentUser.requireId(),
                id,
                goal);
    }

    @PostMapping("/{id}/status")
    public Goal updateStatus(
            @PathVariable Long id,
            @RequestParam GoalStatus status) {

        return goalService.updateStatus(
                currentUser.requireId(),
                id,
                status);
    }

    @PostMapping("/{id}/progress")
    public Goal updateProgress(
            @PathVariable Long id,
            @RequestParam Integer achievedCount) {

        return goalService.updateProgress(
                currentUser.requireId(),
                id,
                achievedCount);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        goalService.delete(
                currentUser.requireId(),
                id);
    }
}