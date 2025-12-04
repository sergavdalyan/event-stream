package com.sporty.eventstream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EventStreamApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventStreamApplication.class, args);
    }

}
