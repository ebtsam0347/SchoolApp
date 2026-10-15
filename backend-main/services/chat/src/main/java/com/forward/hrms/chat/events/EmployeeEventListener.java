package com.forward.hrms.chat.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forward.hrms.chat.entity.ConversationMember;
import com.forward.hrms.chat.repository.ConversationMemberRepository;
import com.forward.hrms.chat.service.ChatPublisher;
import com.forward.hrms.commons.core.tx.Tx;
import com.forward.hrms.commons.employeeevents.EmployeeEvents;
import com.forward.hrms.commons.employeeevents.EmployeeEventsConsumerSupport;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chat's first Kafka consumer. On {@code employee.terminated} the
 * user must lose access to chat: every group's
 * {@code conversation_members} row for the user is deleted, the
 * {@code presence:{userId}} + {@code presence:lastseen:{userId}}
 * Redis keys are dropped, and {@code chat.member.removed} is fanned
 * out to the surviving members so their UI can refresh the roster.
 *
 * <p>Idempotent — Kafka may redeliver; deletes on rows that no longer
 * exist are no-ops, presence-key deletes are unconditional.
 *
 * <p>Closes §3.4 / §0 termination wiring for chat.
 */
@Slf4j
@Component
public class EmployeeEventListener extends EmployeeEventsConsumerSupport {

    private final ConversationMemberRepository members;
    private final ChatPublisher publisher;
    private final StringRedisTemplate redis;

    public EmployeeEventListener(ConversationMemberRepository members,
                                 ChatPublisher publisher,
                                 StringRedisTemplate redis,
                                 @Qualifier("outboxObjectMapper") ObjectMapper json) {
        super(json);
        this.members = members;
        this.publisher = publisher;
        this.redis = redis;
    }

    @KafkaListener(topics = EmployeeEvents.TOPIC, groupId = "chat.employee-events")
    @Transactional
    public void onEmployeeEvent(String message) throws java.io.IOException {
        dispatch(message);
    }

    @Override
    protected void handle(String eventType, JsonNode payload) {
        if (!EmployeeEvents.EMPLOYEE_TERMINATED.equals(eventType)) {
            log.debug("Ignoring employee event type {}", eventType);
            return;
        }
        Long userId = readLong(payload, "userId");
        if (userId == null) return; // employee not ESS-linked = no chat user

        // Snapshot conversation ids the user was in so we can fan-out the
        // removal notice after the deletes commit.
        List<Long> conversationIds = members.findConversationIdsByUserId(userId);
        if (conversationIds.isEmpty()) return;

        for (Long convId : conversationIds) {
            members.findByIdConversationIdAndIdUserId(convId, userId)
                    .ifPresent(this::removeMember);
        }

        // After-commit Redis: drop presence keys + notify survivors.
        long uid = userId;
        Tx.afterCommit(() -> {
            redis.delete("presence:" + uid);
            redis.delete("presence:lastseen:" + uid);
            for (Long convId : conversationIds) {
                List<Long> survivors = members.findUserIdsByConversationId(convId);
                if (survivors.isEmpty()) continue;
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("conversationId", convId);
                data.put("userId", uid);
                data.put("reason", "EMPLOYEE_TERMINATED");
                publisher.publishToUsers(survivors, "chat.member.removed", data);
            }
        });
    }

    private void removeMember(ConversationMember cm) {
        members.delete(cm);
    }
}
