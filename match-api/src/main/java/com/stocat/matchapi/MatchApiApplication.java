package com.stocat.matchapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.stocat.common", "com.stocat.matchapi"})
public class MatchApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MatchApiApplication.class, args);
    }

}
