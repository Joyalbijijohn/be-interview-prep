package com.interview.prep.shortener;

import java.time.Instant;

public record ShortenResponse(String code, String shortUrl, String originalUrl, Instant expiresAt) {
}
