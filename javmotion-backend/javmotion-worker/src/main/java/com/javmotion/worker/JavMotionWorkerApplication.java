package com.javmotion.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Execution worker. Never exposed to the internet — only the API calls it.
 * Step B4 in CHECKLIST.md: /internal/jobs, sandbox runner, workspace cleanup.
 */
@SpringBootApplication
public class JavMotionWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(JavMotionWorkerApplication.class, args);
    }
}
