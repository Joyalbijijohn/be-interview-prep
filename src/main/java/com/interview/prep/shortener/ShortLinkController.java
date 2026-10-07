package com.interview.prep.shortener;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class ShortLinkController {

    private static final String CODE = "{code:[0-9A-Za-z]{1,8}}";

    private final ShortLinkService service;

    public ShortLinkController(ShortLinkService service) {
        this.service = service;
    }

    @PostMapping("/api/links")
    ResponseEntity<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest request) {
        ShortLink link = service.create(request.url(), request.expiresAt());
        String shortUrl = ServletUriComponentsBuilder.fromCurrentContextPath().path("/" + link.getCode())
                .toUriString();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ShortenResponse(link.getCode(), shortUrl, link.getOriginalUrl(), link.getExpiresAt()));
    }

    @GetMapping("/api/links/" + CODE + "/stats")
    StatsResponse stats(@PathVariable String code) {
        return service.stats(code);
    }

    @GetMapping("/" + CODE)
    ResponseEntity<Void> redirect(@PathVariable String code) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, service.visit(code))
                .build();
    }
}
