package com.machinext;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MachiNextApplication {

    public static void main(String[] args) {
        SpringApplication.run(MachiNextApplication.class, args);
    }
}
