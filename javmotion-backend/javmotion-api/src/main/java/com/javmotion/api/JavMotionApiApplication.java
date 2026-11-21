package com.javmotion.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class JavMotionApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(JavMotionApiApplication.class, args);
    }
}
