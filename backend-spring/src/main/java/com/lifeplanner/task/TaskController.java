package com.lifeplanner.task;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.lifeplanner.security.CurrentUser;
import com.lifeplanner.task.TaskEntities.Task;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/tasks")
@SecurityRequirement(name = "bearerAuth")
public class TaskController {

    private final TaskService taskService;
    private final CurrentUser currentUser;

    public TaskController(
            TaskService taskService,
            CurrentUser currentUser) {

        this.taskService = taskService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<Task> list() {
        return taskService.list(currentUser.requireId());
    }

    @GetMapping("/{id}")
    public Task get(@PathVariable Long id) {
        return taskService.get(
                currentUser.requireId(),
                id);
    }

    @PostMapping
    public Task create(@RequestBody Task task) {

        return taskService.create(
                currentUser.requireId(),
                task);
    }

    @PutMapping("/{id}")
    public Task update(
            @PathVariable Long id,
            @RequestBody Task task) {

        return taskService.update(
                currentUser.requireId(),
                id,
                task);
    }

    @PostMapping("/{id}/complete")
    public Task complete(@PathVariable Long id) {

        return taskService.complete(
                currentUser.requireId(),
                id);
    }

    @PostMapping("/{id}/skip")
    public Task skip(@PathVariable Long id) {

        return taskService.skip(
                currentUser.requireId(),
                id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {

        taskService.delete(
                currentUser.requireId(),
                id);
    }
}