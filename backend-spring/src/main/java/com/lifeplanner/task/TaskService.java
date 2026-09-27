package com.lifeplanner.task;

import java.time.Instant;
import java.util.List;
import com.lifeplanner.common.ApiExceptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lifeplanner.task.TaskEntities.Task;
import com.lifeplanner.task.TaskEntities.TaskStatus;

@Service
public class TaskService {

    private final TaskRepository repository;

    public TaskService(TaskRepository repository) {
        this.repository = repository;
    }

    public List<Task> list(Long userId) {
        return repository.findByUserIdOrderByDueDateAscIdAsc(userId);
    }

    public Task get(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Task not found"));
    }

    @Transactional
    public Task create(Long userId, Task task) {

        task.setUserId(userId);
        task.setCompletedAt(null);
        task.setStatus(TaskStatus.PENDING);

        return repository.save(task);
    }

    @Transactional
    public Task update(Long userId, Long id, Task request) {

        Task task = get(userId, id);

        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setCategory(request.getCategory());
        task.setScheduledDate(request.getScheduledDate());
        task.setStartMinute(request.getStartMinute());
        task.setDurationMinutes(request.getDurationMinutes());
        task.setDueDate(request.getDueDate());
        task.setPriority(request.getPriority());
        task.setFlexibility(request.getFlexibility());
        task.setGoalId(request.getGoalId());
        task.setMilestoneId(request.getMilestoneId());
        task.setHabitId(request.getHabitId());

        return repository.save(task);
    }

    @Transactional
    public Task complete(Long userId, Long id) {

        Task task = get(userId, id);

        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(Instant.now());

        return repository.save(task);
    }

    @Transactional
    public Task skip(Long userId, Long id) {

        Task task = get(userId, id);

        task.setStatus(TaskStatus.SKIPPED);
        task.setCompletedAt(null);

        return repository.save(task);
    }

    @Transactional
    public void delete(Long userId, Long id) {

        Task task = get(userId, id);

        repository.delete(task);
    }
}