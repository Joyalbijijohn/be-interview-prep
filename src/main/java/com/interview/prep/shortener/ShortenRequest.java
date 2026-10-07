package com.interview.prep.shortener;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record ShortenRequest(
        @NotBlank(message = "url is required")
        @Size(max = 2048, message = "url must be at most 2048 characters")
        @HttpUrl
        String url,
        @Future(message = "expiresAt must be in the future")
        Instant expiresAt) {
}
