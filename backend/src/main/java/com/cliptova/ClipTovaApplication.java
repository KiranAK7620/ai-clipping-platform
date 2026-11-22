package com.cliptova;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ClipTovaApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClipTovaApplication.class, args);
    }
}
