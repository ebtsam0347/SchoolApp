package com.forward.hrms.employee.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Component
@RequiredArgsConstructor
public class ResignationCompletionJob {

    private final ExitService exits;

    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Karachi")
    public void run() {
        try {
            int n = exits.completeDuePending();
            if (n > 0) log.info("Resignation completion job processed {} pending exits", n);
        } catch (RuntimeException ex) {
            log.error("Resignation completion job failed; will retry tomorrow", ex);
        }
    }
}
