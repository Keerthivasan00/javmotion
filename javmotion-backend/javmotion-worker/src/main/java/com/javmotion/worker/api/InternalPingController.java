package com.javmotion.worker.api;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Placeholder until POST /internal/jobs exists (Step B4). */
@RestController
public class InternalPingController {

    @GetMapping("/internal/ping")
    public Map<String, String> ping() {
        return Map.of("status", "UP", "service", "javmotion-worker");
    }
}
