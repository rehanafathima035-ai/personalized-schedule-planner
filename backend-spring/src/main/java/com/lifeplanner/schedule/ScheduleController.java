
package com.lifeplanner.schedule;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lifeplanner.planner.PlannerDtos;
import com.lifeplanner.schedule.ScheduleEntities.ScheduledItem;
import com.lifeplanner.security.CurrentUser;

@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final CurrentUser currentUser;

    public ScheduleController(
            ScheduleService scheduleService,
            CurrentUser currentUser) {
        this.scheduleService = scheduleService;
        this.currentUser = currentUser;
    }

    @GetMapping("/proposals/pending")
    public List<Map<String, Object>> pending() {
        return scheduleService.pending(currentUser.requireId()).stream()
                .map(proposal -> Map.<String, Object>of(
                        "id", proposal.getId(),
                        "origin", proposal.getOrigin().name(),
                        "strategy", proposal.getStrategy(),
                        "itemCount", proposal.getItems().size(),
                        "createdAt", proposal.getCreatedAt().toString()))
                .toList();
    }

    @PostMapping("/proposals/{id}/approve")
    public Map<String, Object> approve(@PathVariable Long id) {
        return scheduleService.approve(
                currentUser.requireId(),
                id);
    }

    @PostMapping("/proposals/{id}/reject")
    public Map<String, Object> reject(@PathVariable Long id) {
        return scheduleService.reject(
                currentUser.requireId(),
                id);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        return scheduleService.delete(
                currentUser.requireId(),
                id);
    }

    @GetMapping("/today")
    public List<ScheduledItem> today() {
        return scheduleService.forDay(
                currentUser.requireId(),
                LocalDate.now());
    }

    @GetMapping
    public List<ScheduledItem> range(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to) {

        return scheduleService.forRange(
                currentUser.requireId(),
                from,
                to);
    }
    @GetMapping("/conflicts")
    public List<Map<String, Object>> conflicts(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,
            @RequestParam Integer startMinute,
            @RequestParam Integer endMinute) {

        return scheduleService.detectConflicts(
                currentUser.requireId(),
                date,
                startMinute,
                endMinute);
    }
    @GetMapping("/planner-conflicts")
    public PlannerDtos.ConflictResponse plannerConflicts(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,
            @RequestParam Integer startMinute,
            @RequestParam Integer endMinute) {

        return scheduleService.plannerConflicts(
                currentUser.requireId(),
                date,
                startMinute,
                endMinute);
    }
    @PostMapping("/what-if")
    public PlannerDtos.PlanResponse whatIf(
            @org.springframework.web.bind.annotation.RequestBody
            PlannerDtos.WhatIfPayload payload) {

        return scheduleService.whatIf(
                currentUser.requireId(),
                payload);
            }
    @PostMapping("/propose")
    public PlannerDtos.PlanResponse propose(
            @org.springframework.web.bind.annotation.RequestBody
            PlannerDtos.PlanPayload payload) {

        return scheduleService.propose(
                currentUser.requireId(),
                payload);
    }
    @PostMapping("/rebalance")
    public PlannerDtos.PlanResponse rebalance(
            @org.springframework.web.bind.annotation.RequestBody
            PlannerDtos.RebalancePayload payload) {

        return scheduleService.rebalance(
                currentUser.requireId(),
                payload);
    }
}

