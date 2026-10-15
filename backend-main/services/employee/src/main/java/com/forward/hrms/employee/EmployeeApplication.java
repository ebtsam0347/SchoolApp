package com.forward.hrms.employee;

import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.service.EmployeeCodeService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableScheduling
@EnableTransactionManagement
@EntityScan(basePackages = {
        "com.forward.hrms.employee.entity",
        "com.forward.hrms.commons.kafka.outbox",
        "com.forward.hrms.commons.saga",
        "com.forward.hrms.commons.approval.entity"
})
@EnableJpaRepositories(basePackages = {
        "com.forward.hrms.employee.repository",
        "com.forward.hrms.commons.approval.repository"
})
public class EmployeeApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmployeeApplication.class, args);
    }

    // to check test cases
//    @Bean
//    CommandLineRunner init(EmployeeCodeService employeeCodeService) {
//        return args -> {
//            // Initialization logic here
//            var employeeCode = employeeCodeService.allocate();
//            System.out.println("Allocated employee code: " + employeeCode);
//        };
//    }
}
