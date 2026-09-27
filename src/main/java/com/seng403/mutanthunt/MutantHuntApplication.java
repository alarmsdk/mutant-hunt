package com.seng403.mutanthunt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MutantHuntApplication {
    public static void main(String[] args) {
        SpringApplication.run(MutantHuntApplication.class, args);
    }
}
