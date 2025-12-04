package com.sporty.eventstream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableRetry
@EnableAsync
public class EventStreamApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventStreamApplication.class, args);
    }

}
