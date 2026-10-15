package com.forward.hrms.commons.kafka.event;

/**
 * Topic naming convention. Per-service audit topics ({@code &lt;svc&gt;.audit})
 * and a default events topic ({@code &lt;svc&gt;.events}); retry/dlq derived
 * from the source topic.
 */
public final class KafkaTopics {

    private KafkaTopics() {}

    public static String audit(String service) { return service + ".audit"; }
    public static String events(String service) { return service + ".events"; }
    public static String dlq(String topic) { return topic + ".dlq"; }
    public static String retry(String topic, int attempt) { return topic + ".retry-" + attempt; }
}
