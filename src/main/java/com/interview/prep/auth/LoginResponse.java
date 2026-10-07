package com.interview.prep.auth;

public record LoginResponse(String accessToken, String tokenType, long expiresIn) {
}
