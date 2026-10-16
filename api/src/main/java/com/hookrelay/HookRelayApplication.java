package com.hookrelay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.hookrelay")
@EnableScheduling
@EntityScan(basePackages = "com.hookrelay.core.entity")
@EnableJpaRepositories(basePackages = "com.hookrelay.core.repository")
public class HookRelayApplication {

    public static void main(String[] args) {
        SpringApplication.run(HookRelayApplication.class, args);
    }
}
