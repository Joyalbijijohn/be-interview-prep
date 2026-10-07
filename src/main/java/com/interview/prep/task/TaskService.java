package com.interview.prep.task;

import com.interview.prep.common.NotFoundException;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TaskService {

    private final TaskRepository repository;

    public TaskService(TaskRepository repository) {
        this.repository = repository;
    }

    public TaskResponse create(TaskRequest request) {
        Task task = new Task(request.title(), request.description(), statusOrDefault(request), request.dueDate());
        return TaskResponse.from(repository.save(task));
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> list(TaskStatus status) {
        List<Task> tasks = status == null
                ? repository.findAll(Sort.by("id"))
                : repository.findByStatusOrderByIdAsc(status);
        return tasks.stream().map(TaskResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Long id) {
        return TaskResponse.from(find(id));
    }

    public TaskResponse update(Long id, TaskRequest request) {
        Task task = find(id);
        task.update(request.title(), request.description(), statusOrDefault(request), request.dueDate());
        return TaskResponse.from(task);
    }

    public void delete(Long id) {
        repository.delete(find(id));
    }

    private Task find(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Task " + id + " not found"));
    }

    private TaskStatus statusOrDefault(TaskRequest request) {
        return request.status() == null ? TaskStatus.TO_DO : request.status();
    }
}
