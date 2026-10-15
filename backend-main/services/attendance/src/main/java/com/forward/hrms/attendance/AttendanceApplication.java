package com.forward.hrms.attendance;

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
        "com.forward.hrms.attendance.entity",
        "com.forward.hrms.commons.kafka.outbox",
        "com.forward.hrms.commons.approval.entity"
})
@EnableJpaRepositories(basePackages = {
        "com.forward.hrms.attendance.repository",
        "com.forward.hrms.commons.approval.repository"
})
public class AttendanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AttendanceApplication.class, args);
    }
}
