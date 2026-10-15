package com.forward.hrms.bff.client;

import com.forward.hrms.chat.dto.response.ConversationDto;
import com.forward.hrms.chat.dto.response.PresenceDto;
import com.forward.hrms.bff.config.DownstreamProperties;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.UpstreamError;
import com.forward.hrms.commons.web.security.IdentityHeaders;

import lombok.extern.slf4j.Slf4j;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * BFF → chat-service. The only call so far is the delivery ACK the SSE
 * forwarder makes immediately after {@code emitter.send(...)} succeeds
 * for a {@code chat.message} event.
 *
 * <p>The Redis pub/sub callback runs on a listener thread that has no
 * {@code RequestContextHolder} populated, so the commons-web identity
 * forwarding interceptor naturally no-ops on this thread. We synthesize
 * {@code X-User-Id} from the SSE session's userId — chat-service is
 * inside the cluster trust boundary and accepts header-identity from
 * peer services.
 */
@Slf4j
@Component
public class ChatClient {

    private final RestClient http;

    public ChatClient(DownstreamProperties props, RestClient.Builder builder) {
        this.http = builder.baseUrl(props.chat().baseUrl()).build();
    }

    public List<ConversationDto> myConversations() {
        try {
            List<ConversationDto> body = http.get()
                    .uri("/chat/conversations")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return body == null ? List.of() : body;
        } catch (RestClientException ex) {
            throw upstream("my-conversations", ex);
        }
    }

    public List<PresenceDto> presence(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return List.of();
        String csv = userIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
        try {
            List<PresenceDto> body = http.get()
                    .uri(uri -> uri.path("/chat/presence").queryParam("userIds", csv).build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return body == null ? List.of() : body;
        } catch (RestClientException ex) {
            throw upstream("presence", ex);
        }
    }

    /** Best-effort: a Redis blip / chat-service hiccup is logged but never breaks SSE forwarding. */
    public void markDelivered(long messageId, long recipientUserId) {
        try {
            http.post()
                    .uri("/chat/messages/{id}/delivery", messageId)
                    .header(IdentityHeaders.USER_ID, Long.toString(recipientUserId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            log.debug("delivery ACK to chat-service failed (msg {} user {}): {}",
                    messageId, recipientUserId, ex.getMessage());
        }
    }

    private static UpstreamError upstream(String what, RestClientException ex) {
        log.warn("chat-service call failed for {}: {}", what, ex.getMessage());
        return new UpstreamError(CommonErrorCode.DEPENDENCY_UNAVAILABLE,
                "chat-service request failed.", ex);
    }
}
