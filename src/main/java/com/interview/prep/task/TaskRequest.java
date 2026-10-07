package com.interview.prep.task;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record TaskRequest(
        @NotBlank(message = "title is required")
        @Size(max = 100, message = "title must be at most 100 characters")
        String title,
        @Size(max = 2000, message = "description must be at most 2000 characters")
        String description,
        TaskStatus status,
        @FutureOrPresent(message = "dueDate cannot be in the past")
        LocalDate dueDate) {
}
