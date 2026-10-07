package com.interview.prep.shortener;

import java.time.Instant;

public record StatsResponse(String code, String originalUrl, long visitCount, Instant createdAt) {

    static StatsResponse from(ShortLink link) {
        return new StatsResponse(link.getCode(), link.getOriginalUrl(), link.getVisitCount(), link.getCreatedAt());
    }
}
