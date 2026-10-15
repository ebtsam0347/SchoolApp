package com.forward.hrms.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableScheduling
@EnableTransactionManagement
@EntityScan(basePackages = {
        "com.forward.hrms.auth.entity",
        "com.forward.hrms.commons.kafka.outbox"
})
// Default scan from this class's package picks up auth.repository.*. Service
// must declare this even when commons-saga / commons-outbox already do, since
// their @EnableJpaRepositories suppresses Spring Boot's default JPA autoconfig.
@EnableJpaRepositories
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
