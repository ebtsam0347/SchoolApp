package com.forward.hrms.notification.events;

import com.forward.hrms.notification.service.EventDispatcher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * One {@code @KafkaListener} per source service's events topic. Each
 * runs in its own consumer group ({@code notification.<svc>}) so commit
 * offsets and lag metrics stay independent per source — same pattern as
 * audit's per-topic consumer groups.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventConsumer {

    private final EventDispatcher dispatcher;

    @KafkaListener(topics = "auth.events", groupId = "notification.auth")
    public void onAuth(ConsumerRecord<String, String> rec) {
        dispatcher.handle(rec.topic(), rec.value());
    }

    @KafkaListener(topics = "employee.events", groupId = "notification.employee")
    public void onEmployee(ConsumerRecord<String, String> rec) {
        dispatcher.handle(rec.topic(), rec.value());
    }

    @KafkaListener(topics = "attendance.events", groupId = "notification.attendance")
    public void onAttendance(ConsumerRecord<String, String> rec) {
        dispatcher.handle(rec.topic(), rec.value());
    }

    @KafkaListener(topics = "payroll.events", groupId = "notification.payroll")
    public void onPayroll(ConsumerRecord<String, String> rec) {
        dispatcher.handle(rec.topic(), rec.value());
    }

    @KafkaListener(topics = "chat.events", groupId = "notification.chat")
    public void onChat(ConsumerRecord<String, String> rec) {
        dispatcher.handle(rec.topic(), rec.value());
    }
}
