package cn.iantech.trigger.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka 消息监听器。
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "kafka", name = "enabled", havingValue = "true")
public class KafkaMessageListener {

    @KafkaListener(topics = "${kafka.topic:ian-mq}", groupId = "${spring.kafka.consumer.group-id:ian-group}")
    public void onMessage(String message) {
        log.info("接收到 Kafka 消息 {}", message);
    }

}
