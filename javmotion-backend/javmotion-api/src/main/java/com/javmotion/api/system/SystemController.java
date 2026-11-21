package com.javmotion.api.system;

import com.javmotion.api.config.AppProperties;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lightweight endpoint the frontend uses to show "server online". */
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final AppProperties properties;

    public SystemController(AppProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/ping")
    public PingResponse ping() {
        return new PingResponse("UP", "javmotion-api", properties.version(), Instant.now());
    }

    public record PingResponse(String status, String service, String version, Instant time) {}
}
