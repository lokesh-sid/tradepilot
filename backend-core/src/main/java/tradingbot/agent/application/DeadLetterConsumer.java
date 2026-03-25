package tradingbot.agent.application;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Service;

import tradingbot.agent.infrastructure.persistence.DeadLetterEntity;
import tradingbot.agent.infrastructure.repository.DeadLetterRepository;

/**
 * DeadLetterConsumer — listens to every *.DLT topic and:
 *
 * <ol>
 *   <li>Persists the failure to PostgreSQL ({@code dead_letter_events}) for audit/replay.</li>
 *   <li>Logs a structured ERROR with topic, partition, offset, key, and exception details.</li>
 *   <li>Emits a threshold alert (WARN) when the unresolved failure count for a single topic
 *       exceeds {@code agent.dlt.alert-threshold} within the last
 *       {@code agent.dlt.alert-window-minutes} minutes.</li>
 * </ol>
 *
 * <p>Spring Kafka's {@link org.springframework.kafka.listener.DeadLetterPublishingRecoverer}
 * automatically populates the following headers on DLT messages, which this consumer reads:</p>
 * <ul>
 *   <li>{@code kafka_dlt-original-topic}     — original topic name</li>
 *   <li>{@code kafka_dlt-original-partition} — original partition (4-byte big-endian int)</li>
 *   <li>{@code kafka_dlt-original-offset}    — original offset (8-byte big-endian long)</li>
 *   <li>{@code kafka_dlt-exception-fqcn}     — exception class name</li>
 *   <li>{@code kafka_dlt-exception-message}  — exception message</li>
 *   <li>{@code kafka_dlt-exception-stacktrace} — full stack trace</li>
 * </ul>
 */
@Service
public class DeadLetterConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterConsumer.class);

    /** Maximum characters persisted for the message value to avoid bloating the DB. */
    private static final int MAX_VALUE_LENGTH    = 4_000;
    /** Maximum characters persisted for the exception message. */
    private static final int MAX_MSG_LENGTH      = 1_000;
    /** Maximum characters persisted for the stack trace. */
    private static final int MAX_TRACE_LENGTH    = 2_000;

    // -------------------------------------------------------------------------
    // Alert thresholds (externalisable via application.properties)
    // -------------------------------------------------------------------------

    /**
     * Number of unresolved DLT failures within the alert window that triggers a WARN alert.
     * Default: 3.  Set {@code agent.dlt.alert-threshold=0} to disable alerting.
     */
    @Value("${agent.dlt.alert-threshold:3}")
    private int alertThreshold;

    /**
     * Width of the sliding window (minutes) used for threshold counting.
     * Default: 5.
     */
    @Value("${agent.dlt.alert-window-minutes:5}")
    private int alertWindowMinutes;

    private final DeadLetterRepository deadLetterRepository;

    public DeadLetterConsumer(DeadLetterRepository deadLetterRepository) {
        this.deadLetterRepository = deadLetterRepository;
    }

    // -------------------------------------------------------------------------
    // Listener
    // -------------------------------------------------------------------------

    /**
     * Catches every message on any topic whose name ends with {@code .DLT}.
     * A separate consumer group ({@code trading-bot-dlt-auditor}) ensures this listener
     * does not interfere with the main processing consumers.
     */
    @KafkaListener(
        topicPattern  = ".*\\.DLT",
        groupId       = "trading-bot-dlt-auditor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onDeadLetter(ConsumerRecord<String, Object> record) {
        Headers headers = record.headers();

        // --- Extract DLT metadata from Spring Kafka headers -------------------
        String originalTopic  = headerString(headers, KafkaHeaders.DLT_ORIGINAL_TOPIC);
        int    origPartition  = headerInt   (headers, KafkaHeaders.DLT_ORIGINAL_PARTITION,  record.partition());
        long   origOffset     = headerLong  (headers, KafkaHeaders.DLT_ORIGINAL_OFFSET,     record.offset());
        String exceptionFqcn  = headerString(headers, KafkaHeaders.DLT_EXCEPTION_FQCN);
        String exceptionMsg   = headerString(headers, KafkaHeaders.DLT_EXCEPTION_MESSAGE);
        String stackTrace     = headerString(headers, KafkaHeaders.DLT_EXCEPTION_STACKTRACE);

        // Fall back to the DLT topic name minus the ".DLT" suffix when the header is absent.
        if (originalTopic == null || originalTopic.isBlank()) {
            String dltTopic = record.topic();
            originalTopic = dltTopic.endsWith(".DLT")
                    ? dltTopic.substring(0, dltTopic.length() - 4)
                    : dltTopic;
        }

        String valueStr = record.value() != null ? truncate(record.value().toString(), MAX_VALUE_LENGTH) : null;

        // --- Structured error log ---------------------------------------------
        log.error(
            "[DLT] Dead-letter event received. " +
            "dltTopic={} originalTopic={} origPartition={} origOffset={} key={} " +
            "exceptionType={} exceptionMessage={}",
            record.topic(), originalTopic, origPartition, origOffset,
            record.key(),
            simpleClassName(exceptionFqcn),
            truncate(exceptionMsg, 200));

        // --- Persist to PostgreSQL --------------------------------------------
        DeadLetterEntity entity = new DeadLetterEntity(
            UUID.randomUUID().toString(),
            originalTopic,
            origPartition,
            origOffset,
            record.key(),
            valueStr,
            simpleClassName(exceptionFqcn),
            truncate(exceptionMsg,  MAX_MSG_LENGTH),
            truncate(stackTrace,    MAX_TRACE_LENGTH),
            Instant.now()
        );

        try {
            deadLetterRepository.save(entity);
            log.debug("[DLT] Persisted dead-letter event id={} to dead_letter_events.", entity.getId());
        } catch (Exception saveEx) {
            // Do not re-throw — a save failure must not re-route back to the DLT.
            log.error("[DLT] Failed to persist dead-letter entity for topic={} offset={}: {}",
                    originalTopic, origOffset, saveEx.getMessage(), saveEx);
        }

        // --- Threshold alerting -----------------------------------------------
        checkAlertThreshold(originalTopic);
    }

    // -------------------------------------------------------------------------
    // Alert threshold logic
    // -------------------------------------------------------------------------

    /**
     * Counts unresolved DLT failures for {@code originalTopic} in the last
     * {@code alertWindowMinutes} and logs a WARN if the count exceeds {@code alertThreshold}.
     *
     * <p>Extend this method to send a Slack/PagerDuty/email notification when
     * a dedicated alerting integration is available.</p>
     */
    private void checkAlertThreshold(String originalTopic) {
        if (alertThreshold <= 0) {
            return; // alerting disabled
        }
        try {
            Instant windowStart = Instant.now().minus(Duration.ofMinutes(alertWindowMinutes));
            long recentCount = deadLetterRepository.countRecentUnresolvedByTopic(originalTopic, windowStart);

            if (recentCount >= alertThreshold) {
                log.warn(
                    "[DLT-ALERT] *** Threshold breached for topic='{}': {} unresolved failures " +
                    "in the last {} minute(s). Investigate dead_letter_events table or replay. ***",
                    originalTopic, recentCount, alertWindowMinutes);

                // TODO: integrate with your alerting system here, e.g.:
                // slackNotifier.send("#trading-alerts",
                //     String.format("DLT alert: %d failures on %s in %d min",
                //         recentCount, originalTopic, alertWindowMinutes));
            }
        } catch (Exception countEx) {
            // Non-fatal — alerting must not affect consumer commit.
            log.warn("[DLT] Could not evaluate alert threshold for topic={}: {}",
                    originalTopic, countEx.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Header parsing helpers
    // -------------------------------------------------------------------------

    private static String headerString(Headers headers, String name) {
        Header h = headers.lastHeader(name);
        return h != null && h.value() != null ? new String(h.value()) : null;
    }

    private static int headerInt(Headers headers, String name, int fallback) {
        Header h = headers.lastHeader(name);
        if (h == null || h.value() == null || h.value().length < 4) return fallback;
        return ByteBuffer.wrap(h.value()).getInt();
    }

    private static long headerLong(Headers headers, String name, long fallback) {
        Header h = headers.lastHeader(name);
        if (h == null || h.value() == null || h.value().length < 8) return fallback;
        return ByteBuffer.wrap(h.value()).getLong();
    }

    // -------------------------------------------------------------------------
    // Utility helpers
    // -------------------------------------------------------------------------

    /** Returns the simple class name from a fully-qualified class name (or the input unchanged). */
    private static String simpleClassName(String fqcn) {
        if (fqcn == null || fqcn.isBlank()) return "Unknown";
        int dot = fqcn.lastIndexOf('.');
        return dot >= 0 ? fqcn.substring(dot + 1) : fqcn;
    }

    /** Truncates a string to {@code max} characters; returns null if the input is null. */
    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
