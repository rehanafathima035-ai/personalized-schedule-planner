package com.lifeplanner.habit;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import com.lifeplanner.security.CurrentUser;

@RestController
@RequestMapping("/api/habits")
@SecurityRequirement(name = "bearerAuth")
public class HabitController {

    private final HabitService habitService;
    private final HabitStreakService habitStreakService;
    private final CurrentUser currentUser;

    public HabitController(
            HabitService habitService,
            HabitStreakService habitStreakService,
            CurrentUser currentUser) {
        this.habitService = habitService;
        this.habitStreakService = habitStreakService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<HabitDtos.HabitSummary> list() {
        return habitService.list(currentUser.requireId());
    }

    @GetMapping("/{id}")
    public HabitDtos.HabitSummary get(@PathVariable Long id) {
        return habitService.get(currentUser.requireId(), id);
    }

    @GetMapping("/{id}/streak")
    public HabitStreakService.StreakResult streak(@PathVariable Long id) {

        Long userId = currentUser.requireId();

        habitService.get(userId, id);

        return habitStreakService.calculate(
                userId,
                id,
                java.time.LocalDate.now().minusYears(1),
                java.time.LocalDate.now());
    }

    @PostMapping("/interpret")
    public Map<String, Object> interpret(@RequestBody Map<String, String> body) {
        return habitService.interpret(body.getOrDefault("text", ""));
    }

    @PostMapping("/preview")
    public Map<String, Object> preview(
            @Valid @RequestBody HabitDtos.PreviewRequest request) {

        HabitService.ProposalView view = habitService.preview(
                currentUser.requireId(), request);

        return Map.of(
                "proposalId", view.proposal().getId(),
                "status", "PENDING_APPROVAL",
                "proposal", view.response().proposal(),
                "alternatives", view.response().alternatives(),
                "balance", view.response().balance(),
                "message", "Nothing has been saved yet. Approve to add this to your schedule.");
    }
}