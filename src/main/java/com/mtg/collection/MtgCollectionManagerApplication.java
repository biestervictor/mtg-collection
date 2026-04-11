package com.mtg.collection;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MtgCollectionManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MtgCollectionManagerApplication.class, args);
    }
}
